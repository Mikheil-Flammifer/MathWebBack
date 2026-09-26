package com.mathweb.service.impl;

import com.mathweb.dto.request.*;
import com.mathweb.dto.response.AuthResponse;
import com.mathweb.dto.response.UserResponse;
import com.mathweb.entity.OtpCode;
import com.mathweb.entity.RefreshToken;
import com.mathweb.entity.User;
import com.mathweb.enums.Role;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.OtpException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.OtpCodeRepository;
import com.mathweb.repository.RefreshTokenRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.security.JwtTokenProvider;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.AuthService;
import com.mathweb.service.EmailService;
import com.mathweb.util.OtpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Value("${otp.expiration-minutes}")
    private int otpExpirationMinutes;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public AuthServiceImpl(UserRepository userRepository,
                           OtpCodeRepository otpCodeRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           AuthenticationManager authenticationManager,
                           EmailService emailService) {
        this.userRepository = userRepository;
        this.otpCodeRepository = otpCodeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.STUDENT)
                .emailVerified(false)
                .active(true)
                .build();

        userRepository.save(user);

        // Generate and send OTP
        String otp = OtpUtil.generateOtp();
        saveOtp(user, otp, "EMAIL_VERIFICATION");
        emailService.sendOtpEmail(user.getEmail(), user.getFirstName(), otp, "EMAIL_VERIFICATION");

        log.info("User registered: {}", user.getEmail());

        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public void verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        OtpCode otpCode = otpCodeRepository
                .findByUserAndCodeAndUsedFalse(user, request.getCode())
                .orElseThrow(() -> new OtpException("Invalid OTP code"));

        if (!otpCode.isValid()) {
            throw new OtpException("OTP code has expired");
        }

        if (!otpCode.getPurpose().equals(request.getPurpose())) {
            throw new OtpException("Invalid OTP purpose");
        }

        // Mark OTP as used
        otpCode.setUsed(true);
        otpCodeRepository.save(otpCode);

        // Verify email if that was the purpose
        if ("EMAIL_VERIFICATION".equals(request.getPurpose())) {
            user.setEmailVerified(true);
            userRepository.save(user);
            emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName());
        }

        log.info("OTP verified for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void resendOtp(ResendOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Invalidate all existing OTPs for this purpose
        otpCodeRepository.invalidateAllByUserAndPurpose(user, request.getPurpose());

        // Generate and send new OTP
        String otp = OtpUtil.generateOtp();
        saveOtp(user, otp, request.getPurpose());
        emailService.sendOtpEmail(user.getEmail(), user.getFirstName(), otp, request.getPurpose());

        log.info("OTP resent for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().toLowerCase(),
                        request.getPassword()
                )
        );

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        if (!userPrincipal.isEmailVerified()) {
            throw new BadRequestException("Please verify your email before logging in");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(userPrincipal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userPrincipal);

        // Save refresh token
        saveRefreshToken(userPrincipal, refreshToken);

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        log.info("User logged in: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(mapToUserResponse(user))
                .build();
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository
                .findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (!storedToken.isValid()) {
            throw new BadRequestException("Refresh token has expired or been revoked");
        }

        User user = storedToken.getUser();
        UserPrincipal userPrincipal = UserPrincipal.fromUser(user);

        String newAccessToken = jwtTokenProvider.generateAccessToken(userPrincipal);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userPrincipal);

        // Replace old refresh token
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);
        saveRefreshToken(userPrincipal, newRefreshToken);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .user(mapToUserResponse(user))
                .build();
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        otpCodeRepository.invalidateAllByUserAndPurpose(user, "PASSWORD_RESET");

        String otp = OtpUtil.generateOtp();
        saveOtp(user, otp, "PASSWORD_RESET");
        emailService.sendOtpEmail(user.getEmail(), user.getFirstName(), otp, "PASSWORD_RESET");

        log.info("Password reset OTP sent for: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        OtpCode otpCode = otpCodeRepository
                .findByUserAndCodeAndUsedFalse(user, request.getCode())
                .orElseThrow(() -> new OtpException("Invalid OTP code"));

        if (!otpCode.isValid()) {
            throw new OtpException("OTP code has expired");
        }

        otpCode.setUsed(true);
        otpCodeRepository.save(otpCode);

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke all refresh tokens on password reset
        refreshTokenRepository.revokeAllByUser(user);

        log.info("Password reset for: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            // No token provided — nothing to do, still return success
            return;
        }

        try {
            if (jwtTokenProvider.validateToken(token)) {
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    refreshTokenRepository.revokeAllByUser(user);
                    log.info("User {} logged out successfully", user.getEmail());
                }
            }
        } catch (Exception e) {
            // Token may be expired but we still logout successfully
            log.warn("Logout called with invalid token, ignoring: {}", e.getMessage());
        }
    }

    // ===== PRIVATE HELPERS =====

    private void saveOtp(User user, String code, String purpose) {
        OtpCode otpCode = OtpCode.builder()
                .user(user)
                .code(code)
                .purpose(purpose)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes))
                .used(false)
                .build();
        otpCodeRepository.save(otpCode);
    }

    private void saveRefreshToken(UserPrincipal userPrincipal, String token) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Remove existing refresh token if any
        refreshTokenRepository.findByUser(user)
                .ifPresent(existing -> {
                    existing.setRevoked(true);
                    refreshTokenRepository.save(existing);
                    refreshTokenRepository.delete(existing);
                    refreshTokenRepository.flush();
                });

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now()
                        .plusSeconds(refreshTokenExpiration / 1000))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .emailVerified(user.getEmailVerified())
                .active(user.getActive())
                .avatarUrl(user.getAvatarUrl())
                .hasActiveSubscription(user.hasActiveSubscription())
                .createdAt(user.getCreatedAt())
                .build();
    }
}