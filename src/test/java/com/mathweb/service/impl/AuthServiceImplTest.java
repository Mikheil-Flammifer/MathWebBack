
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

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OtpCodeRepository otpCodeRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private EmailService emailService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User user;
    private UserResponse userResponse;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "otpExpirationMinutes", 10);
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 86400000L);

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

        userResponse = UserResponse.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .role(Role.STUDENT)
                .emailVerified(true)
                .active(true)
                .build();

        userPrincipal = UserPrincipal.fromUser(user);
    }

    // --------------------------------------------------
    // REGISTER
    // --------------------------------------------------

    @Test
    void register_createsUserAndSendsVerificationOtp() {
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

        UserResponse result = authService.register(request);

        assertThat(result.getEmail()).isEqualTo("john@example.com");
        assertThat(result.getRole()).isEqualTo(Role.STUDENT);
        assertThat(result.getEmailVerified()).isFalse();
        assertThat(result.getActive()).isTrue();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("john@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getRole()).isEqualTo(Role.STUDENT);

        ArgumentCaptor<OtpCode> otpCaptor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepository).save(otpCaptor.capture());

        OtpCode savedOtp = otpCaptor.getValue();
        assertThat(savedOtp.getPurpose()).isEqualTo("EMAIL_VERIFICATION");
        assertThat(savedOtp.getCode()).matches("\\d{6}");
        assertThat(savedOtp.getUsed()).isFalse();
        assertThat(savedOtp.getExpiresAt()).isAfter(LocalDateTime.now());

        verify(emailService).sendOtpEmail(
                eq("john@example.com"),
                eq("John"),
                matches("\\d{6}"),
                eq("EMAIL_VERIFICATION")
        );

        verify(rateLimitService).checkOtpRate("JOHN@example.com");
    }

    @Test
    void register_throwsExceptionWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("john@example.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email is already registered");

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder, otpCodeRepository, emailService);
    }

    // --------------------------------------------------
    // VERIFY OTP
    // --------------------------------------------------

    @Test
    void verifyOtp_verifiesEmailWithCorrectCode() {
        OtpCode otp = OtpCode.builder()
                .user(user)
                .code("123456")
                .purpose("EMAIL_VERIFICATION")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .build();

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail(" JOHN@EXAMPLE.COM ");
        request.setCode("123456");
        request.setPurpose("EMAIL_VERIFICATION");

        when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(user));

        when(otpCodeRepository
                .findTopByUserAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                        user, "EMAIL_VERIFICATION"))
                .thenReturn(Optional.of(otp));

        authService.verifyOtp(request);

        assertThat(otp.getUsed()).isTrue();
        assertThat(user.getEmailVerified()).isTrue();

        verify(otpCodeRepository).save(otp);
        verify(userRepository).save(user);
        verify(emailService).sendWelcomeEmail("john@example.com", "John");
    }

    @Test
    void verifyOtp_throwsExceptionWhenUserDoesNotExist() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("missing@example.com");
        request.setCode("123456");
        request.setPurpose("EMAIL_VERIFICATION");

        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyOtp(request))
                .isInstanceOf(OtpException.class)
                .hasMessage("Invalid or expired code");

        verifyNoInteractions(otpCodeRepository, emailService);
    }

    @Test
    void verifyOtp_throwsExceptionWhenCodeIsIncorrect() {
        OtpCode otp = OtpCode.builder()
                .user(user)
                .code("123456")
                .purpose("EMAIL_VERIFICATION")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .build();

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("john@example.com");
        request.setCode("999999");
        request.setPurpose("EMAIL_VERIFICATION");

        when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(user));

        when(otpCodeRepository
                .findTopByUserAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                        user, "EMAIL_VERIFICATION"))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> authService.verifyOtp(request))
                .isInstanceOf(OtpException.class)
                .hasMessage("Invalid or expired code");

        assertThat(otp.getAttempts()).isEqualTo(1);
        assertThat(otp.getUsed()).isFalse();

        verify(otpCodeRepository).save(otp);
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(emailService);
    }

    @Test
    void verifyOtp_throwsExceptionWhenCodeIsExpired() {
        OtpCode otp = OtpCode.builder()
                .user(user)
                .code("123456")
                .purpose("EMAIL_VERIFICATION")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .used(false)
                .build();

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("john@example.com");
        request.setCode("123456");
        request.setPurpose("EMAIL_VERIFICATION");

        when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(user));

        when(otpCodeRepository
                .findTopByUserAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                        user, "EMAIL_VERIFICATION"))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> authService.verifyOtp(request))
                .isInstanceOf(OtpException.class);

        assertThat(otp.getUsed()).isTrue();
        verify(otpCodeRepository).save(otp);
        verify(userRepository, never()).save(any(User.class));
    }

    // --------------------------------------------------
    // RESEND OTP
    // --------------------------------------------------

    @Test
    void resendOtp_invalidatesOldCodesAndSendsNewCode() {
        ResendOtpRequest request = new ResendOtpRequest();
        request.setEmail("john@example.com");
        request.setPurpose("EMAIL_VERIFICATION");

        when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(user));

        authService.resendOtp(request);

        verify(rateLimitService).checkOtpRate("john@example.com");
        verify(otpCodeRepository)
                .invalidateAllByUserAndPurpose(user, "EMAIL_VERIFICATION");
        verify(otpCodeRepository).save(argThat(otp ->
                otp.getPurpose().equals("EMAIL_VERIFICATION")
                        && otp.getCode().matches("\\d{6}")
                        && !otp.getUsed()
        ));
        verify(emailService).sendOtpEmail(
                eq("john@example.com"),
                eq("John"),
                matches("\\d{6}"),
                eq("EMAIL_VERIFICATION")
        );
    }

    @Test
    void resendOtp_throwsExceptionWhenUserDoesNotExist() {
        ResendOtpRequest request = new ResendOtpRequest();
        request.setEmail("missing@example.com");
        request.setPurpose("EMAIL_VERIFICATION");

        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resendOtp(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");

        verifyNoInteractions(otpCodeRepository, emailService);
    }

    // --------------------------------------------------
    // LOGIN
    // --------------------------------------------------

    @Test
    void login_returnsTokensWhenCredentialsAreValid() {
        LoginRequest request = new LoginRequest();
        request.setEmail("JOHN@example.com");
        request.setPassword("password123");

        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any(
                UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        when(authentication.getPrincipal()).thenReturn(userPrincipal);

        when(jwtTokenProvider.generateAccessToken(userPrincipal))
                .thenReturn("access-token");

        when(jwtTokenProvider.generateRefreshToken(userPrincipal))
                .thenReturn("refresh-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByUser(user))
                .thenReturn(Optional.empty());

        AuthResponse result = authService.login(request);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(result.getTokenType()).isEqualTo("Bearer");
        assertThat(result.getUser().getEmail()).isEqualTo("john@example.com");

        verify(rateLimitService).checkLoginRate("john@example.com");
        verify(rateLimitService).resetLoginRate("john@example.com");

        verify(refreshTokenRepository).save(argThat(token ->
                token.getToken().equals("refresh-token")
                        && token.getUser().equals(user)
                        && !token.getRevoked()
                        && token.getExpiresAt().isAfter(LocalDateTime.now())
        ));
    }

    @Test
    void login_throwsExceptionWhenEmailIsNotVerified() {
        userPrincipal.setEmailVerified(false);

        LoginRequest request = new LoginRequest();
        request.setEmail("john@example.com");
        request.setPassword("password123");

        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any(
                UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        when(authentication.getPrincipal()).thenReturn(userPrincipal);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Please verify your email before logging in");

        verify(rateLimitService).resetLoginRate("john@example.com");
        verifyNoInteractions(jwtTokenProvider, refreshTokenRepository);
    }

    // --------------------------------------------------
    // REFRESH TOKEN
    // --------------------------------------------------

    @Test
    void refreshToken_replacesOldTokenAndReturnsNewTokens() {
        RefreshToken oldToken = RefreshToken.builder()
                .user(user)
                .token("old-refresh-token")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false)
                .build();

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh-token");

        when(refreshTokenRepository.findByToken("old-refresh-token"))
                .thenReturn(Optional.of(oldToken));

        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class)))
                .thenReturn("new-access-token");

        when(jwtTokenProvider.generateRefreshToken(any(UserPrincipal.class)))
                .thenReturn("new-refresh-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByUser(user))
                .thenReturn(Optional.empty());

        AuthResponse result = authService.refreshToken(request);

        assertThat(result.getAccessToken()).isEqualTo("new-access-token");
        assertThat(result.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(oldToken.getRevoked()).isTrue();

        verify(refreshTokenRepository).save(oldToken);
        verify(refreshTokenRepository).save(argThat(token ->
                token.getToken().equals("new-refresh-token")
                        && !token.getRevoked()
        ));
    }

    @Test
    void refreshToken_throwsExceptionWhenTokenDoesNotExist() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("unknown-token");

        when(refreshTokenRepository.findByToken("unknown-token"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid refresh token");

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void refreshToken_throwsExceptionWhenAccountIsInactive() {
        user.setActive(false);

        RefreshToken storedToken = RefreshToken.builder()
                .user(user)
                .token("old-refresh-token")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false)
                .build();

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh-token");

        when(refreshTokenRepository.findByToken("old-refresh-token"))
                .thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Account is not active");

        verifyNoInteractions(jwtTokenProvider);
    }

    // --------------------------------------------------
    // FORGOT PASSWORD
    // --------------------------------------------------

    @Test
    void forgotPassword_sendsPasswordResetOtpWhenUserExists() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(" JOHN@EXAMPLE.COM ");

        when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(user));

        authService.forgotPassword(request);

        verify(rateLimitService).checkOtpRate("john@example.com");
        verify(otpCodeRepository)
                .invalidateAllByUserAndPurpose(user, "PASSWORD_RESET");

        verify(otpCodeRepository).save(argThat(otp ->
                otp.getPurpose().equals("PASSWORD_RESET")
                        && otp.getCode().matches("\\d{6}")
                        && !otp.getUsed()
        ));

        verify(emailService).sendOtpEmail(
                eq("john@example.com"),
                eq("John"),
                matches("\\d{6}"),
                eq("PASSWORD_RESET")
        );
    }

    @Test
    void forgotPassword_doesNotRevealWhetherUserExists() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("missing@example.com");

        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatCode(() -> authService.forgotPassword(request))
                .doesNotThrowAnyException();

        verify(rateLimitService).checkOtpRate("missing@example.com");
        verifyNoInteractions(otpCodeRepository, emailService);
    }

    // --------------------------------------------------
    // RESET PASSWORD
    // --------------------------------------------------

    @Test
    void resetPassword_updatesPasswordAndRevokesRefreshTokens() {
        OtpCode otp = OtpCode.builder()
                .user(user)
                .code("123456")
                .purpose("PASSWORD_RESET")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail(" JOHN@EXAMPLE.COM ");
        request.setCode("123456");
        request.setNewPassword("newPassword123");

        when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(user));

        when(otpCodeRepository
                .findTopByUserAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                        user, "PASSWORD_RESET"))
                .thenReturn(Optional.of(otp));

        when(passwordEncoder.encode("newPassword123"))
                .thenReturn("new-encoded-password");

        authService.resetPassword(request);

        assertThat(otp.getUsed()).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo("new-encoded-password");

        verify(otpCodeRepository).save(otp);
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    void resetPassword_throwsExceptionWhenUserDoesNotExist() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("missing@example.com");
        request.setCode("123456");
        request.setNewPassword("newPassword123");

        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(OtpException.class)
                .hasMessage("Invalid or expired code");

        verifyNoInteractions(passwordEncoder, refreshTokenRepository);
    }

    // --------------------------------------------------
    // REVOKE REFRESH TOKEN
    // --------------------------------------------------

    @Test
    void revokeRefreshToken_revokesExistingToken() {
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token("refresh-token")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken("refresh-token"))
                .thenReturn(Optional.of(token));

        authService.revokeRefreshToken("refresh-token");

        assertThat(token.getRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void revokeRefreshToken_doesNothingForBlankToken() {
        authService.revokeRefreshToken(" ");

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void revokeRefreshToken_doesNothingWhenTokenDoesNotExist() {
        when(refreshTokenRepository.findByToken("unknown-token"))
                .thenReturn(Optional.empty());

        authService.revokeRefreshToken("unknown-token");

        verify(refreshTokenRepository, never()).save(any());
    }

    // --------------------------------------------------
    // LOGOUT
    // --------------------------------------------------

    @Test
    void logout_blacklistsAccessTokenAndRevokesRefreshTokens() {
        String token = "valid-access-token";
        Date expiration = new Date(System.currentTimeMillis() + 60000);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getExpirationFromToken(token)).thenReturn(expiration);
        when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        authService.logout(token);

        verify(tokenBlacklistService).blacklist(
                eq(token),
                any(LocalDateTime.class)
        );

        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    void logout_doesNothingForInvalidToken() {
        String token = "invalid-token";

        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        assertThatCode(() -> authService.logout(token))
                .doesNotThrowAnyException();

        verifyNoInteractions(tokenBlacklistService, refreshTokenRepository);
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void logout_doesNothingForBlankToken() {
        authService.logout(" ");

        verifyNoInteractions(
                jwtTokenProvider,
                tokenBlacklistService,
                refreshTokenRepository
        );
    }

    @Test
    void logout_doesNotPropagateExceptions() {
        String token = "problematic-token";

        when(jwtTokenProvider.validateToken(token))
                .thenThrow(new RuntimeException("JWT processing failed"));

        assertThatCode(() -> authService.logout(token))
                .doesNotThrowAnyException();
    }
}
