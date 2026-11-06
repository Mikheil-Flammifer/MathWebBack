package com.mathweb.security;

import com.mathweb.entity.OtpCode;
import com.mathweb.entity.RefreshToken;
import com.mathweb.entity.User;
import com.mathweb.enums.Role;
import com.mathweb.repository.OtpCodeRepository;
import com.mathweb.repository.RefreshTokenRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end auth flow on the real filter chain and the real PostgreSQL:
 * register -> OTP -> login -> refresh -> logout, plus password reset.
 *
 * Location: src/test/java/com/mathweb/security/AuthFlowIT.java
 *
 * Notes
 *  - The OTP is read from the otp_codes table, so EmailService is not mocked and the
 *    shared Spring context from AbstractIntegrationTest is reused.
 *  - Every test uses a fresh random e-mail, so per-e-mail rate limits never collide.
 *  - Request bodies are built as Maps, so the tests do not depend on the DTO classes.
 *  - "/api/probe/unmapped" is an authenticated-only path with no controller:
 *    401 = not authenticated, anything else = the JWT filter accepted the caller.
 */
class AuthFlowIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Str0ngPass!234";
    private static final String XHR = "XMLHttpRequest";
    private static final String PROBE = "/api/probe/unmapped";

    @Autowired UserRepository userRepository;
    @Autowired OtpCodeRepository otpCodeRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private String uniqueEmail() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    private User createVerifiedUser(String email, String password) {
        return userRepository.save(User.builder()
                .firstName("Test")
                .lastName("User")
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(Role.STUDENT)
                .emailVerified(true)
                .active(true)
                .build());
    }

    private ResultActions postJson(String path, Map<String, ?> body, Cookie... cookies) throws Exception {
        var req = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (cookies.length > 0) {
            req = req.cookie(cookies);
        }
        return mockMvc.perform(req);
    }

    /** Same as postJson but with the header the CSRF filter requires when cookies are sent. */
    private ResultActions postXhr(String path, Cookie... cookies) throws Exception {
        var req = post(path).header("X-Requested-With", XHR);
        if (cookies.length > 0) {
            req = req.cookie(cookies);
        }
        return mockMvc.perform(req);
    }

    private ResultActions register(String email) throws Exception {
        return postJson("/api/auth/register", Map.of(
                "firstName", "Test",
                "lastName", "User",
                "email", email,
                "password", PASSWORD));
    }

    private ResultActions verify(String email, String code) throws Exception {
        // The JSON name of the code field in VerifyOtpRequest was not visible to me, so both
        // common names are sent (Jackson ignores unknown properties). Once you know the real
        // name, keep only that one.
        return postJson("/api/auth/verify-otp", Map.of("email", email, "code", code, "otp", code));
    }

    /** Asserts 200 and, on failure, prints the response body so the real reason is visible. */
    private void verifyOk(String email, String code) throws Exception {
        MvcResult r = verify(email, code).andReturn();
        assertThat(r.getResponse().getStatus())
                .as("verify-otp response body: %s", r.getResponse().getContentAsString())
                .isEqualTo(200);
    }

    private MvcResult login(String email, String password) throws Exception {
        return postJson("/api/auth/login", Map.of("email", email, "password", password))
                .andReturn();
    }

    private String latestOtp(String email, String purpose) {
        User user = userRepository.findByEmail(email).orElseThrow();
        return otpCodeRepository.findTopByUserAndPurposeOrderByCreatedAtDesc(user, purpose)
                .orElseThrow().getCode();
    }

    private OtpCode latestOtpEntity(String email, String purpose) {
        User user = userRepository.findByEmail(email).orElseThrow();
        return otpCodeRepository.findTopByUserAndPurposeOrderByCreatedAtDesc(user, purpose).orElseThrow();
    }

    private static String wrongCode(String realCode) {
        return "000000".equals(realCode) ? "111111" : "000000";
    }

    private static Cookie cookie(MvcResult result, String name) {
        Cookie c = result.getResponse().getCookie(name);
        assertThat(c).as("cookie " + name + " must be set").isNotNull();
        return c;
    }

    private static String setCookieHeader(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Set-Cookie header for " + name));
    }

    private ResultActions probe(Cookie access) throws Exception {
        return mockMvc.perform(get(PROBE).cookie(access));
    }

    private static void assertNot401(ResultActions actions) throws Exception {
        assertThat(actions.andReturn().getResponse().getStatus()).isNotEqualTo(401);
    }

    // ---------------------------------------------------------------
    // Register + OTP
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Register and e-mail verification")
    class RegisterAndVerify {

        @Test
        void register_createsUnverifiedStudent_withHashedPassword_andOtp() throws Exception {
            String email = uniqueEmail();

            register(email)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.email").value(email))
                    .andExpect(jsonPath("$.data.role").value("STUDENT"))
                    .andExpect(jsonPath("$.data.emailVerified").value(false));

            User saved = userRepository.findByEmail(email).orElseThrow();
            assertThat(saved.getEmailVerified()).isFalse();
            assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
            assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
            assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();

            assertThat(latestOtp(email, "EMAIL_VERIFICATION")).matches("\\d{6}");
        }

        @Test
        void register_duplicateEmail_isRejected() throws Exception {
            String email = uniqueEmail();
            createVerifiedUser(email, PASSWORD);

            register(email).andExpect(status().isBadRequest());
        }

        @Test
        void register_storesEmailLowercased() throws Exception {
            String email = uniqueEmail();
            register(email.toUpperCase()).andExpect(status().isCreated());

            assertThat(userRepository.findByEmail(email)).isPresent();
        }

        @Test
        void login_beforeVerification_isRejected_andSetsNoCookies() throws Exception {
            String email = uniqueEmail();
            register(email).andExpect(status().isCreated());

            MvcResult result = login(email, PASSWORD);

            // An unverified account is rejected by Spring Security itself (disabled user),
            // so the answer is 401, not the 400 of AuthServiceImpl's own "verify your email" check.
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(result.getResponse().getCookie(AuthCookieService.ACCESS)).isNull();
            assertThat(result.getResponse().getCookie(AuthCookieService.REFRESH)).isNull();
        }

        @Test
        void verifyOtp_withCorrectCode_marksUserVerified_andBurnsTheCode() throws Exception {
            String email = uniqueEmail();
            register(email).andExpect(status().isCreated());
            String code = latestOtp(email, "EMAIL_VERIFICATION");

            verifyOk(email, code);

            assertThat(userRepository.findByEmail(email).orElseThrow().getEmailVerified()).isTrue();
            assertThat(latestOtpEntity(email, "EMAIL_VERIFICATION").getUsed()).isTrue();

            // The same code cannot be used twice
            assertThat(verify(email, code).andReturn().getResponse().getStatus()).isBetween(400, 499);
        }

        @Test
        void verifyOtp_withWrongCode_countsTheAttempt_andKeepsUserUnverified() throws Exception {
            String email = uniqueEmail();
            register(email).andExpect(status().isCreated());
            String real = latestOtp(email, "EMAIL_VERIFICATION");

            assertThat(verify(email, wrongCode(real)).andReturn().getResponse().getStatus())
                    .isBetween(400, 499);

            OtpCode otp = latestOtpEntity(email, "EMAIL_VERIFICATION");
            assertThat(otp.getAttempts()).isEqualTo(1);
            assertThat(otp.getUsed()).isFalse();
            assertThat(userRepository.findByEmail(email).orElseThrow().getEmailVerified()).isFalse();
        }

        @Test
        void verifyOtp_fiveWrongAttempts_burnsTheCode_evenForTheCorrectOneAfterwards() throws Exception {
            String email = uniqueEmail();
            register(email).andExpect(status().isCreated());
            String real = latestOtp(email, "EMAIL_VERIFICATION");

            for (int i = 0; i < 5; i++) {
                assertThat(verify(email, wrongCode(real)).andReturn().getResponse().getStatus())
                        .isBetween(400, 499);
            }

            OtpCode burnt = latestOtpEntity(email, "EMAIL_VERIFICATION");
            assertThat(burnt.getAttempts()).isEqualTo(5);
            assertThat(burnt.getUsed()).isTrue();

            // noRollbackFor = OtpException: the counter survived the exception.
            // The correct code no longer works.
            assertThat(verify(email, real).andReturn().getResponse().getStatus()).isBetween(400, 499);
            assertThat(userRepository.findByEmail(email).orElseThrow().getEmailVerified()).isFalse();
        }

        @Test
        void verifyOtp_expiredCode_isRejected() throws Exception {
            String email = uniqueEmail();
            register(email).andExpect(status().isCreated());
            OtpCode otp = latestOtpEntity(email, "EMAIL_VERIFICATION");
            otp.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            otpCodeRepository.save(otp);

            assertThat(verify(email, otp.getCode()).andReturn().getResponse().getStatus())
                    .isBetween(400, 499);
            assertThat(userRepository.findByEmail(email).orElseThrow().getEmailVerified()).isFalse();
        }

        @Test
        void verifyOtp_unknownEmail_looksLikeAnyOtherFailure() throws Exception {
            MvcResult unknown = verify(uniqueEmail(), "123456").andReturn();

            String email = uniqueEmail();
            register(email).andExpect(status().isCreated());
            MvcResult wrong = verify(email, wrongCode(latestOtp(email, "EMAIL_VERIFICATION"))).andReturn();

            // Same status and same message: no user enumeration through the verify endpoint
            assertThat(unknown.getResponse().getStatus()).isEqualTo(wrong.getResponse().getStatus());
            assertThat(unknown.getResponse().getContentAsString())
                    .isEqualTo(wrong.getResponse().getContentAsString());
        }
    }

    // ---------------------------------------------------------------
    // Login
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        void fullFlow_register_verify_login() throws Exception {
            String email = uniqueEmail();

            register(email).andExpect(status().isCreated());
            verifyOk(email, latestOtp(email, "EMAIL_VERIFICATION"));

            MvcResult result = login(email, PASSWORD);

            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(cookie(result, AuthCookieService.ACCESS).getValue()).isNotBlank();
            assertThat(cookie(result, AuthCookieService.REFRESH).getValue()).isNotBlank();
        }

        @Test
        void login_setsHttpOnlyCookies_withTheRightPaths_andKeepsTokensOutOfTheBody() throws Exception {
            String email = uniqueEmail();
            createVerifiedUser(email, PASSWORD);

            MvcResult result = login(email, PASSWORD);

            String access = setCookieHeader(result, AuthCookieService.ACCESS);
            String refresh = setCookieHeader(result, AuthCookieService.REFRESH);
            assertThat(access).contains("HttpOnly").contains("Path=/").doesNotContain("Path=/api/auth");
            assertThat(refresh).contains("HttpOnly").contains("Path=/api/auth");

            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain(cookie(result, AuthCookieService.ACCESS).getValue());
            assertThat(body).doesNotContain(cookie(result, AuthCookieService.REFRESH).getValue());
            assertThat(body).contains(email);
        }

        @Test
        void login_storesExactlyOneRefreshToken_andReplacesItOnTheNextLogin() throws Exception {
            String email = uniqueEmail();
            User user = createVerifiedUser(email, PASSWORD);

            String first = cookie(login(email, PASSWORD), AuthCookieService.REFRESH).getValue();
            assertThat(refreshTokenRepository.findByUser(user)).isPresent();

            String second = cookie(login(email, PASSWORD), AuthCookieService.REFRESH).getValue();

            assertThat(second).isNotEqualTo(first);
            assertThat(refreshTokenRepository.findByToken(first)).isEmpty();
            assertThat(refreshTokenRepository.findByToken(second)).isPresent();
        }

        @Test
        void login_wrongPassword_unknownEmail_areRejected_withoutCookies() throws Exception {
            String email = uniqueEmail();
            createVerifiedUser(email, PASSWORD);

            MvcResult wrongPassword = login(email, "WrongPass!999");
            MvcResult unknownEmail = login(uniqueEmail(), PASSWORD);

            assertThat(wrongPassword.getResponse().getStatus()).isBetween(400, 401);
            assertThat(unknownEmail.getResponse().getStatus())
                    .isEqualTo(wrongPassword.getResponse().getStatus());
            assertThat(wrongPassword.getResponse().getCookie(AuthCookieService.ACCESS)).isNull();
            assertThat(unknownEmail.getResponse().getCookie(AuthCookieService.ACCESS)).isNull();
        }

        @Test
        void accessCookie_authenticatesTheCaller() throws Exception {
            String email = uniqueEmail();
            createVerifiedUser(email, PASSWORD);
            MvcResult result = login(email, PASSWORD);

            assertNot401(probe(cookie(result, AuthCookieService.ACCESS)));
        }

        @Test
        void bearerHeader_authenticatesTheCaller_withoutCookies() throws Exception {
            String email = uniqueEmail();
            createVerifiedUser(email, PASSWORD);
            String token = cookie(login(email, PASSWORD), AuthCookieService.ACCESS).getValue();

            assertNot401(mockMvc.perform(get(PROBE).header("Authorization", "Bearer " + token)));
        }

        @Test
        void deactivatedUser_withAValidAccessToken_isNoLongerAuthenticated() throws Exception {
            String email = uniqueEmail();
            User user = createVerifiedUser(email, PASSWORD);
            Cookie access = cookie(login(email, PASSWORD), AuthCookieService.ACCESS);
            assertNot401(probe(access));

            user.setActive(false);
            userRepository.save(user);

            probe(access).andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------
    // Refresh
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Refresh")
    class Refresh {

        @Test
        void refresh_rotatesTheToken_andIssuesNewCookies() throws Exception {
            String email = uniqueEmail();
            createVerifiedUser(email, PASSWORD);
            MvcResult loggedIn = login(email, PASSWORD);
            Cookie oldRefresh = cookie(loggedIn, AuthCookieService.REFRESH);
            Cookie oldAccess = cookie(loggedIn, AuthCookieService.ACCESS);

            MvcResult refreshed = postXhr("/api/auth/refresh", oldRefresh)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                    .andReturn();

            Cookie newRefresh = cookie(refreshed, AuthCookieService.REFRESH);
            Cookie newAccess = cookie(refreshed, AuthCookieService.ACCESS);
            assertThat(newRefresh.getValue()).isNotEqualTo(oldRefresh.getValue());
            assertThat(newAccess.getValue()).isNotEqualTo(oldAccess.getValue());

            assertThat(refreshTokenRepository.findByToken(oldRefresh.getValue())).isEmpty();
            RefreshToken stored = refreshTokenRepository.findByToken(newRefresh.getValue()).orElseThrow();
            assertThat(stored.isValid()).isTrue();

            assertNot401(probe(newAccess));
        }

        @Test
        void refresh_reusingTheOldRefreshToken_isRejected() throws Exception {
            String email = uniqueEmail();
            createVerifiedUser(email, PASSWORD);
            Cookie oldRefresh = cookie(login(email, PASSWORD), AuthCookieService.REFRESH);

            postXhr("/api/auth/refresh", oldRefresh).andExpect(status().isOk());

            // Replay of the rotated token
            postXhr("/api/auth/refresh", oldRefresh).andExpect(status().isBadRequest());
        }

        @Test
        void refresh_withoutCookie_isRejected() throws Exception {
            mockMvc.perform(post("/api/auth/refresh"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        void refresh_withForgedToken_isRejected() throws Exception {
            postXhr("/api/auth/refresh", new Cookie(AuthCookieService.REFRESH, "forged.token.value"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void refresh_withOversizedToken_isRejectedBeforeTouchingTheDatabase() throws Exception {
            postXhr("/api/auth/refresh", new Cookie(AuthCookieService.REFRESH, "a".repeat(2001)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void refresh_withoutCsrfHeader_isForbidden_andDoesNotRotateTheToken() throws Exception {
            String email = uniqueEmail();
            User user = createVerifiedUser(email, PASSWORD);
            Cookie refresh = cookie(login(email, PASSWORD), AuthCookieService.REFRESH);

            mockMvc.perform(post("/api/auth/refresh").cookie(refresh))
                    .andExpect(status().isForbidden());

            RefreshToken stored = refreshTokenRepository.findByUser(user).orElseThrow();
            assertThat(stored.getToken()).isEqualTo(refresh.getValue());
            assertThat(stored.isValid()).isTrue();
        }

        @Test
        void refresh_forDeactivatedUser_isRejected() throws Exception {
            String email = uniqueEmail();
            User user = createVerifiedUser(email, PASSWORD);
            Cookie refresh = cookie(login(email, PASSWORD), AuthCookieService.REFRESH);

            user.setActive(false);
            userRepository.save(user);

            postXhr("/api/auth/refresh", refresh).andExpect(status().isBadRequest());
        }
    }

    // ---------------------------------------------------------------
    // Logout
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Logout")
    class Logout {

        @Test
        void logout_clearsCookies_blacklistsTheAccessToken_andRevokesTheRefreshToken() throws Exception {
            String email = uniqueEmail();
            User user = createVerifiedUser(email, PASSWORD);
            MvcResult loggedIn = login(email, PASSWORD);
            Cookie access = cookie(loggedIn, AuthCookieService.ACCESS);
            Cookie refresh = cookie(loggedIn, AuthCookieService.REFRESH);
            assertNot401(probe(access));

            MvcResult loggedOut = postXhr("/api/auth/logout", access, refresh)
                    .andExpect(status().isOk())
                    .andReturn();

            // Both cookies expired in the browser
            assertThat(setCookieHeader(loggedOut, AuthCookieService.ACCESS)).contains("Max-Age=0");
            assertThat(setCookieHeader(loggedOut, AuthCookieService.REFRESH)).contains("Max-Age=0");

            // The stolen/old access token is dead even though its signature and expiry are still fine
            probe(access).andExpect(status().isUnauthorized());
            mockMvc.perform(get(PROBE).header("Authorization", "Bearer " + access.getValue()))
                    .andExpect(status().isUnauthorized());

            // The refresh token is revoked server side
            assertThat(refreshTokenRepository.findByUser(user).orElseThrow().isValid()).isFalse();
            postXhr("/api/auth/refresh", refresh).andExpect(status().isBadRequest());
        }

        @Test
        void logout_withoutCsrfHeader_isForbidden_andTheSessionStaysValid() throws Exception {
            String email = uniqueEmail();
            createVerifiedUser(email, PASSWORD);
            MvcResult loggedIn = login(email, PASSWORD);
            Cookie access = cookie(loggedIn, AuthCookieService.ACCESS);
            Cookie refresh = cookie(loggedIn, AuthCookieService.REFRESH);

            // A cross-site form post cannot log the user out
            mockMvc.perform(post("/api/auth/logout").cookie(access, refresh))
                    .andExpect(status().isForbidden());

            assertNot401(probe(access));
        }

        @Test
        void logout_withoutAnyCookies_isHarmless() throws Exception {
            postXhr("/api/auth/logout").andExpect(status().isOk());
        }

        @Test
        void logout_withGarbageCookies_isHarmless() throws Exception {
            postXhr("/api/auth/logout",
                    new Cookie(AuthCookieService.ACCESS, "garbage"),
                    new Cookie(AuthCookieService.REFRESH, "garbage"))
                    .andExpect(status().isOk());
        }

        @Test
        void logout_canLogInAgain_afterwards() throws Exception {
            String email = uniqueEmail();
            createVerifiedUser(email, PASSWORD);
            MvcResult first = login(email, PASSWORD);
            postXhr("/api/auth/logout",
                    cookie(first, AuthCookieService.ACCESS),
                    cookie(first, AuthCookieService.REFRESH)).andExpect(status().isOk());

            MvcResult second = login(email, PASSWORD);

            assertThat(second.getResponse().getStatus()).isEqualTo(200);
            assertNot401(probe(cookie(second, AuthCookieService.ACCESS)));
        }
    }

    // ---------------------------------------------------------------
    // Password reset
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Forgot and reset password")
    class PasswordReset {

        private static final String NEW_PASSWORD = "N3wStrongPass!567";

        @Test
        void forgotPassword_answersTheSame_forKnownAndUnknownEmails() throws Exception {
            String known = uniqueEmail();
            createVerifiedUser(known, PASSWORD);

            MvcResult a = postJson("/api/auth/forgot-password", Map.of("email", known)).andReturn();
            MvcResult b = postJson("/api/auth/forgot-password", Map.of("email", uniqueEmail())).andReturn();

            assertThat(a.getResponse().getStatus()).isEqualTo(200);
            assertThat(b.getResponse().getStatus()).isEqualTo(200);
            assertThat(a.getResponse().getContentAsString()).isEqualTo(b.getResponse().getContentAsString());
        }

        @Test
        void resetPassword_changesThePassword_andKillsExistingSessions() throws Exception {
            String email = uniqueEmail();
            User user = createVerifiedUser(email, PASSWORD);
            Cookie oldRefresh = cookie(login(email, PASSWORD), AuthCookieService.REFRESH);

            postJson("/api/auth/forgot-password", Map.of("email", email)).andExpect(status().isOk());
            String code = latestOtp(email, "PASSWORD_RESET");

            postJson("/api/auth/reset-password", Map.of(
                    "email", email, "code", code, "newPassword", NEW_PASSWORD))
                    .andExpect(status().isOk());

            // Old password is dead, new one works
            assertThat(login(email, PASSWORD).getResponse().getStatus()).isBetween(400, 401);
            assertThat(login(email, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);

            // The refresh token from before the reset was revoked
            assertThat(refreshTokenRepository.findByToken(oldRefresh.getValue())
                    .map(RefreshToken::isValid).orElse(false)).isFalse();
        }

        @Test
        void resetPassword_withWrongCode_leavesThePasswordUntouched() throws Exception {
            String email = uniqueEmail();
            createVerifiedUser(email, PASSWORD);
            postJson("/api/auth/forgot-password", Map.of("email", email)).andExpect(status().isOk());
            String real = latestOtp(email, "PASSWORD_RESET");

            assertThat(postJson("/api/auth/reset-password", Map.of(
                    "email", email, "code", wrongCode(real), "newPassword", NEW_PASSWORD))
                    .andReturn().getResponse().getStatus()).isBetween(400, 499);

            assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(200);
        }

        @Test
        void emailVerificationCode_cannotBeUsedToResetAPassword() throws Exception {
            String email = uniqueEmail();
            register(email).andExpect(status().isCreated());
            String verificationCode = latestOtp(email, "EMAIL_VERIFICATION");

            assertThat(postJson("/api/auth/reset-password", Map.of(
                    "email", email, "code", verificationCode, "newPassword", NEW_PASSWORD))
                    .andReturn().getResponse().getStatus()).isBetween(400, 499);

            User user = userRepository.findByEmail(email).orElseThrow();
            assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        }
    }
}