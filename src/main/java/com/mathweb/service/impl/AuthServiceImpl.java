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
import com.mathweb.observability.OperationalMetrics;
import com.mathweb.repository.OtpCodeRepository;
import com.mathweb.repository.RefreshTokenRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.security.JwtTokenProvider;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.AuthService;
import com.mathweb.service.EmailService;
import com.mathweb.service.FileStorageService;
import com.mathweb.util.OtpUtil;
import com.mathweb.util.SanitizationUtil;
import com.mathweb.util.TokenHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int MAX_OTP_ATTEMPTS = 5;

    private static final Logger log =
            LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RateLimitService rateLimitService;
    private final FileStorageService fileStorageService;
    private final OperationalMetrics operationalMetrics;

    @Value("${otp.expiration-minutes}")
    private int otpExpirationMinutes;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Value("${otp.pepper}")
    private String otpPepper;

    public AuthServiceImpl(
            UserRepository userRepository,
            OtpCodeRepository otpCodeRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            AuthenticationManager authenticationManager,
            EmailService emailService,
            TokenBlacklistService tokenBlacklistService,
            RateLimitService rateLimitService,
            FileStorageService fileStorageService,
            OperationalMetrics operationalMetrics
    ) {
        this.userRepository = userRepository;
        this.otpCodeRepository = otpCodeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.rateLimitService = rateLimitService;
        this.fileStorageService = fileStorageService;
        this.operationalMetrics = operationalMetrics;
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        rateLimitService.checkOtpRate(email);

        if (userRepository.existsByEmail(email)) {
            log.info("Registration requested for an existing account");
            return;
        }

        String firstName =
                SanitizationUtil.sanitizeText(request.getFirstName());
        String lastName =
                SanitizationUtil.sanitizeText(request.getLastName());

        if (firstName.length() < 2 || lastName.length() < 2) {
            throw new BadRequestException(
                    "First and last name must be at least 2 characters"
            );
        }

        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.STUDENT)
                .emailVerified(false)
                .active(true)
                .build();

        userRepository.save(user);

        String otp = OtpUtil.generateOtp();
        saveOtp(user, otp, "EMAIL_VERIFICATION");

        emailService.sendOtpEmail(
                user.getEmail(),
                user.getFirstName(),
                otp,
                "EMAIL_VERIFICATION"
        );

        log.info("User registered: id={}", user.getId());
    }

    @Override
    @Transactional(noRollbackFor = OtpException.class)
    public void verifyOtp(VerifyOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        rateLimitService.checkOtpVerificationRate(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new OtpException("Invalid or expired code"));

        OtpCode otpCode = checkCode(
                user,
                "EMAIL_VERIFICATION",
                request.getCode()
        );

        otpCode.setUsed(true);
        otpCodeRepository.save(otpCode);

        user.setEmailVerified(true);
        userRepository.save(user);

        emailService.sendWelcomeEmail(
                user.getEmail(),
                user.getFirstName()
        );

        log.info("Email verified for user {}", user.getId());
    }

    @Override
    @Transactional
    public void resendOtp(ResendOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        rateLimitService.checkOtpRate(email);

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null
                || ("EMAIL_VERIFICATION".equals(request.getPurpose())
                && Boolean.TRUE.equals(user.getEmailVerified()))) {
            return;
        }

        otpCodeRepository.invalidateAllByUserAndPurpose(
                user,
                request.getPurpose()
        );

        String otp = OtpUtil.generateOtp();

        saveOtp(user, otp, request.getPurpose());

        emailService.sendOtpEmail(
                user.getEmail(),
                user.getFirstName(),
                otp,
                request.getPurpose()
        );

        log.info("OTP resend was queued for user {}", user.getId());
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase();

        rateLimitService.checkLoginRate(email);

        Authentication authentication;

        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            request.getPassword()
                    )
            );
        } catch (AuthenticationException exception) {
            operationalMetrics.recordAuthenticationFailure();
            throw exception;
        }

        UserPrincipal userPrincipal =
                (UserPrincipal) authentication.getPrincipal();

        rateLimitService.resetLoginRate(email);

        if (!userPrincipal.isEmailVerified()) {
            throw new BadRequestException(
                    "Please verify your email before logging in"
            );
        }

        String accessToken =
                jwtTokenProvider.generateAccessToken(userPrincipal);

        String refreshToken =
                jwtTokenProvider.generateRefreshToken(userPrincipal);

        saveRefreshToken(userPrincipal, refreshToken);

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        log.info("User logged in: id={}", user.getId());

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
        String tokenHash =
                TokenHashUtil.sha256(request.getRefreshToken());

        RefreshToken storedToken = refreshTokenRepository
                .findByTokenForUpdate(tokenHash)
                .orElseThrow(() ->
                        new BadRequestException("Invalid refresh token"));

        if (!storedToken.isValid()) {
            if (!storedToken.isExpired()
                    && Boolean.TRUE.equals(storedToken.getRevoked())) {
                revokeFamily(storedToken);

                log.warn(
                        "Refresh-token reuse detected for user {} in family {}",
                        storedToken.getUser().getId(),
                        storedToken.getTokenFamilyId()
                );
            }

            throw new BadRequestException(
                    "Refresh token has expired or been revoked"
            );
        }

        User user = storedToken.getUser();

        if (!Boolean.TRUE.equals(user.getActive())
                || !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BadRequestException("Account is not active");
        }

        UserPrincipal userPrincipal = UserPrincipal.fromUser(user);

        String newAccessToken =
                jwtTokenProvider.generateAccessToken(userPrincipal);

        String newRefreshToken =
                jwtTokenProvider.generateRefreshToken(userPrincipal);

        storedToken.setRevoked(true);
        storedToken.setUsedAt(LocalDateTime.now());
        storedToken.setReplacedAt(LocalDateTime.now());

        refreshTokenRepository.save(storedToken);

        saveRefreshToken(
                userPrincipal,
                newRefreshToken,
                storedToken.getTokenFamilyId()
        );

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
        String email = request.getEmail().trim().toLowerCase();

        rateLimitService.checkOtpRate(email);

        userRepository.findByEmail(email).ifPresent(user -> {
            otpCodeRepository.invalidateAllByUserAndPurpose(
                    user,
                    "PASSWORD_RESET"
            );

            String otp = OtpUtil.generateOtp();

            saveOtp(user, otp, "PASSWORD_RESET");

            emailService.sendOtpEmail(
                    user.getEmail(),
                    user.getFirstName(),
                    otp,
                    "PASSWORD_RESET"
            );

            log.info(
                    "Password reset OTP sent for user {}",
                    user.getId()
            );
        });
    }

    @Override
    @Transactional(noRollbackFor = OtpException.class)
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        rateLimitService.checkPasswordResetRate(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new OtpException("Invalid or expired code"));

        OtpCode otpCode = checkCode(
                user,
                "PASSWORD_RESET",
                request.getCode()
        );

        otpCode.setUsed(true);
        otpCodeRepository.save(otpCode);

        user.setPasswordHash(
                passwordEncoder.encode(request.getNewPassword())
        );

        userRepository.save(user);
        refreshTokenRepository.revokeAllByUser(user);

        log.info("Password reset for user {}", user.getId());
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        refreshTokenRepository
                .findByToken(TokenHashUtil.sha256(token))
                .ifPresent(refreshToken -> {
                    refreshToken.setRevoked(true);
                    refreshTokenRepository.save(refreshToken);
                });
    }

    @Override
    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        try {
            if (!jwtTokenProvider.validateToken(token)) {
                return;
            }

            java.util.Date expiration =
                    jwtTokenProvider.getExpirationFromToken(token);

            String tokenId = jwtTokenProvider.getTokenId(token);

            if (expiration != null
                    && tokenId != null
                    && !tokenId.isBlank()) {
                tokenBlacklistService.blacklist(
                        tokenId,
                        expiration.toInstant()
                );
            }

            log.info("Access token logged out");
        } catch (Exception exception) {
            log.warn(
                    "Logout called with invalid token: {}",
                    exception.getMessage()
            );
        }
    }

    private void saveOtp(User user, String code, String purpose) {
        OtpCode otpCode = OtpCode.builder()
                .user(user)
                .codeHash(hashOtp(user, purpose, code))
                .purpose(purpose)
                .expiresAt(
                        LocalDateTime.now()
                                .plusMinutes(otpExpirationMinutes)
                )
                .used(false)
                .build();

        otpCodeRepository.save(otpCode);
    }

    private String hashOtp(User user, String purpose, String code) {
        return TokenHashUtil.hmacSha256(
                otpPepper,
                "otp:" + user.getId() + ":" + purpose + ":" + code
        );
    }

    private void saveRefreshToken(
            UserPrincipal userPrincipal,
            String token
    ) {
        saveRefreshToken(userPrincipal, token, UUID.randomUUID());
    }

    private void saveRefreshToken(
            UserPrincipal userPrincipal,
            String token,
            UUID tokenFamilyId
    ) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(TokenHashUtil.sha256(token))
                .tokenFamilyId(
                        tokenFamilyId == null
                                ? UUID.randomUUID()
                                : tokenFamilyId
                )
                .expiresAt(
                        LocalDateTime.now()
                                .plusSeconds(refreshTokenExpiration / 1000)
                )
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
    }

    private OtpCode checkCode(
            User user,
            String purpose,
            String submitted
    ) {
        OtpCode otp = otpCodeRepository
                .findTopByUserAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                        user,
                        purpose
                )
                .orElseThrow(() ->
                        new OtpException("Invalid or expired code"));

        if (otp.isExpired() || otp.getAttempts() >= MAX_OTP_ATTEMPTS) {
            otp.setUsed(true);
            otpCodeRepository.save(otp);
            throw new OtpException("Invalid or expired code");
        }

        String submittedHash = submitted == null
                ? null
                : hashOtp(user, purpose, submitted);

        boolean match = submittedHash != null
                && MessageDigest.isEqual(
                otp.getCodeHash()
                        .getBytes(StandardCharsets.UTF_8),
                submittedHash.getBytes(StandardCharsets.UTF_8)
        );

        if (!match) {
            otp.setAttempts(otp.getAttempts() + 1);

            if (otp.getAttempts() >= MAX_OTP_ATTEMPTS) {
                otp.setUsed(true);
            }

            otpCodeRepository.save(otp);
            throw new OtpException("Invalid or expired code");
        }

        return otp;
    }

    private void revokeFamily(RefreshToken token) {
        if (token.getTokenFamilyId() == null) {
            refreshTokenRepository.revokeAllByUser(token.getUser());
            return;
        }

        refreshTokenRepository.revokeFamily(
                token.getUser(),
                token.getTokenFamilyId()
        );
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
                .avatarUrl(
                        fileStorageService.getFileUrl(user.getAvatarUrl())
                )
                .hasActiveSubscription(user.hasActiveSubscription())
                .createdAt(user.getCreatedAt())
                .build();
    }
}