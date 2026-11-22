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
import com.mathweb.service.EmailService;
import com.mathweb.service.FileStorageService;
import com.mathweb.util.TokenHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String OTP_PEPPER =
            "test-only-otp-pepper-that-is-at-least-32-bytes";

    @Mock private UserRepository userRepository;
    @Mock private OtpCodeRepository otpCodeRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private FileStorageService fileStorageService;
    @Mock private EmailService emailService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private RateLimitService rateLimitService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User user;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "otpPepper", OTP_PEPPER);
        ReflectionTestUtils.setField(authService, "otpExpirationMinutes", 10);
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 86_400_000L);

        user = User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .passwordHash("encoded-password")
                .role(Role.STUDENT)
                .emailVerified(true)
                .active(true)
                .build();
        user.setId(1L);

        userPrincipal = UserPrincipal.fromUser(user);
    }

    private String hashOtp(String code, String purpose) {
        return TokenHashUtil.hmacSha256(
                OTP_PEPPER,
                "otp:" + user.getId() + ":" + purpose + ":" + code
        );
    }

    private OtpCode otp(String code, String purpose) {
        return OtpCode.builder()
                .user(user)
                .codeHash(hashOtp(code, purpose))
                .purpose(purpose)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .build();
    }

    @Test
    void register_cleansControlCharactersFromNames() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("  Jo\u0001hn  ");
        request.setLastName("Doe");
        request.setEmail("john@example.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getFirstName()).isEqualTo("John");
    }

    @Test
    void register_storesOnlyHashedOtpAndEmailsPlainOtp() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("JOHN@example.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail("JOHN@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        authService.register(request);

        ArgumentCaptor<OtpCode> otpCaptor = ArgumentCaptor.forClass(OtpCode.class);
        ArgumentCaptor<String> sentOtpCaptor = ArgumentCaptor.forClass(String.class);

        verify(otpCodeRepository).save(otpCaptor.capture());
        verify(emailService).sendOtpEmail(
                eq("john@example.com"),
                eq("John"),
                sentOtpCaptor.capture(),
                eq("EMAIL_VERIFICATION")
        );

        OtpCode savedOtp = otpCaptor.getValue();
        String sentOtp = sentOtpCaptor.getValue();

        assertThat(sentOtp).matches("\\d{6}");
        assertThat(savedOtp.getCodeHash()).matches("[0-9a-f]{64}");
        assertThat(savedOtp.getCodeHash())
                .isEqualTo(TokenHashUtil.hmacSha256(
                        OTP_PEPPER,
                        "otp:1:EMAIL_VERIFICATION:" + sentOtp
                ));
        assertThat(savedOtp.getCodeHash()).isNotEqualTo(sentOtp);
        assertThat(savedOtp.getUsed()).isFalse();
    }

    @Test
    void register_throwsWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("john@example.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email is already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyOtp_verifiesCorrectPepperedHash() {
        OtpCode otp = otp("123456", "EMAIL_VERIFICATION");

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail(" JOHN@EXAMPLE.COM ");
        request.setCode("123456");
        request.setPurpose("EMAIL_VERIFICATION");

        when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(user));
        when(otpCodeRepository.findTopByUserAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                user, "EMAIL_VERIFICATION")).thenReturn(Optional.of(otp));

        authService.verifyOtp(request);

        assertThat(otp.getUsed()).isTrue();
        assertThat(user.getEmailVerified()).isTrue();
        verify(emailService).sendWelcomeEmail("john@example.com", "John");
    }

    @Test
    void verifyOtp_rejectsIncorrectCode() {
        OtpCode otp = otp("123456", "EMAIL_VERIFICATION");

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("john@example.com");
        request.setCode("999999");
        request.setPurpose("EMAIL_VERIFICATION");

        when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(user));
        when(otpCodeRepository.findTopByUserAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                user, "EMAIL_VERIFICATION")).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> authService.verifyOtp(request))
                .isInstanceOf(OtpException.class);

        assertThat(otp.getAttempts()).isEqualTo(1);
        assertThat(otp.getUsed()).isFalse();
    }

    @Test
    void verifyOtp_rejectsExpiredCode() {
        OtpCode otp = otp("123456", "EMAIL_VERIFICATION");
        otp.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("john@example.com");
        request.setCode("123456");
        request.setPurpose("EMAIL_VERIFICATION");

        when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(user));
        when(otpCodeRepository.findTopByUserAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                user, "EMAIL_VERIFICATION")).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> authService.verifyOtp(request))
                .isInstanceOf(OtpException.class);

        assertThat(otp.getUsed()).isTrue();
    }

    @Test
    void resendOtp_storesHashAndEmailsPlainCode() {
        ResendOtpRequest request = new ResendOtpRequest();
        request.setEmail("john@example.com");
        request.setPurpose("EMAIL_VERIFICATION");

        when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(user));

        authService.resendOtp(request);

        verify(otpCodeRepository)
                .invalidateAllByUserAndPurpose(user, "EMAIL_VERIFICATION");

        verify(otpCodeRepository).save(argThat(saved ->
                saved.getPurpose().equals("EMAIL_VERIFICATION")
                        && saved.getCodeHash().matches("[0-9a-f]{64}")
                        && !saved.getUsed()
        ));
    }

    @Test
    void login_storesSha256RefreshTokenWithNewFamily() {
        LoginRequest request = new LoginRequest();
        request.setEmail("JOHN@example.com");
        request.setPassword("password123");

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(jwtTokenProvider.generateAccessToken(userPrincipal))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(userPrincipal))
                .thenReturn("refresh-token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AuthResponse result = authService.login(request);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");

        verify(refreshTokenRepository).save(argThat(saved ->
                saved.getToken().equals(TokenHashUtil.sha256("refresh-token"))
                        && saved.getTokenFamilyId() != null
                        && !saved.getRevoked()
        ));

        verify(refreshTokenRepository, never()).revokeAllByUser(any());
    }

    @Test
    void refreshToken_rotatesTokenInsideSameFamily() {
        UUID familyId = UUID.randomUUID();

        RefreshToken oldToken = RefreshToken.builder()
                .user(user)
                .token(TokenHashUtil.sha256("old-refresh-token"))
                .tokenFamilyId(familyId)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false)
                .build();

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh-token");

        when(refreshTokenRepository.findByTokenForUpdate(
                TokenHashUtil.sha256("old-refresh-token")))
                .thenReturn(Optional.of(oldToken));
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class)))
                .thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(any(UserPrincipal.class)))
                .thenReturn("new-refresh-token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AuthResponse result = authService.refreshToken(request);

        assertThat(result.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(oldToken.getRevoked()).isTrue();

        verify(refreshTokenRepository).save(argThat(saved ->
                saved.getToken().equals(TokenHashUtil.sha256("new-refresh-token"))
                        && saved.getTokenFamilyId().equals(familyId)
                        && !saved.getRevoked()
        ));
    }

    @Test
    void refreshToken_reuseRevokesOnlyItsFamily() {
        UUID familyId = UUID.randomUUID();

        RefreshToken reusedToken = RefreshToken.builder()
                .user(user)
                .token(TokenHashUtil.sha256("already-used-token"))
                .tokenFamilyId(familyId)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revoked(true)
                .build();

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("already-used-token");

        when(refreshTokenRepository.findByTokenForUpdate(
                TokenHashUtil.sha256("already-used-token")))
                .thenReturn(Optional.of(reusedToken));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BadRequestException.class);

        verify(refreshTokenRepository).revokeFamily(user, familyId);
        verify(refreshTokenRepository, never()).revokeAllByUser(user);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void refreshToken_rejectsUnknownToken() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("unknown-token");

        when(refreshTokenRepository.findByTokenForUpdate(
                TokenHashUtil.sha256("unknown-token")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void forgotPassword_storesPepperedOtpHash() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("john@example.com");

        when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(user));

        authService.forgotPassword(request);

        verify(otpCodeRepository).save(argThat(saved ->
                saved.getPurpose().equals("PASSWORD_RESET")
                        && saved.getCodeHash().matches("[0-9a-f]{64}")
                        && !saved.getUsed()
        ));
    }

    @Test
    void resetPassword_revokesAllFamilies() {
        OtpCode otp = otp("123456", "PASSWORD_RESET");

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("john@example.com");
        request.setCode("123456");
        request.setNewPassword("newPassword123");

        when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(user));
        when(otpCodeRepository.findTopByUserAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                user, "PASSWORD_RESET")).thenReturn(Optional.of(otp));
        when(passwordEncoder.encode("newPassword123"))
                .thenReturn("new-encoded-password");

        authService.resetPassword(request);

        assertThat(otp.getUsed()).isTrue();
        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    void revokeRefreshToken_hashesBeforeLookup() {
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(TokenHashUtil.sha256("refresh-token"))
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken(TokenHashUtil.sha256("refresh-token")))
                .thenReturn(Optional.of(token));

        authService.revokeRefreshToken("refresh-token");

        assertThat(token.getRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void logout_blacklistsOnlyAccessToken() {
        String token = "valid-access-token";
        Date expiration = new Date(System.currentTimeMillis() + 60_000);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getExpirationFromToken(token)).thenReturn(expiration);
        when(jwtTokenProvider.getTokenId(token)).thenReturn("test-jti");

        authService.logout(token);

        verify(tokenBlacklistService).blacklist(
                eq("test-jti"),
                any(Instant.class)
        );

        verifyNoInteractions(refreshTokenRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void logout_ignoresInvalidToken() {
        when(jwtTokenProvider.validateToken("invalid-token")).thenReturn(false);

        authService.logout("invalid-token");

        verifyNoInteractions(tokenBlacklistService, refreshTokenRepository);
    }
}