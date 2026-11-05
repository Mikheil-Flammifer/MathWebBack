package com.mathweb.security;

import com.mathweb.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the rules in SecurityConfig against the real filter chain.
 *
 * Location: src/test/java/com/mathweb/security/SecurityConfigIT.java
 *
 * "Probe" paths such as /api/quests/probe/unmapped match a security rule but have no
 * controller behind them. A request that passes security therefore ends in 404 (or any
 * non-401/403 status), without running controller code that needs a real principal.
 * The tests only assert on the security decision: 401, 403 or "let through".
 */
class SecurityConfigIT extends AbstractIntegrationTest {

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    /** role == null means anonymous. */
    private ResultActions call(String method, String path, String role) throws Exception {
        var req = request(HttpMethod.valueOf(method), path)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (role != null) {
            req = req.with(user("test-" + role.toLowerCase()).roles(role));
        }
        return mockMvc.perform(req);
    }

    private static ResultMatcher passedSecurity() {
        return result -> assertThat(result.getResponse().getStatus())
                .as("request should not be rejected by security")
                .isNotIn(401, 403);
    }

    // ---------------------------------------------------------------
    // Anonymous / public
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Public and anonymous access")
    class PublicAccess {

        @Test
        void adminEndpoint_withoutAuth_returns401() throws Exception {
            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void garbageBearerToken_returns401() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer not-a-real-jwt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void garbageAccessCookie_returns401() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                            .cookie(new Cookie(AuthCookieService.ACCESS, "not-a-real-jwt")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void authEndpoints_arePublic() throws Exception {
            // Empty body -> validation error (400), but never 401/403 from security
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void plans_arePublic() throws Exception {
            mockMvc.perform(get("/api/plans")).andExpect(passedSecurity());
        }

        @Test
        void imageUploads_arePublic() throws Exception {
            mockMvc.perform(get("/uploads/images/probe.png")).andExpect(passedSecurity());
        }

        @Test
        void videoUploads_areDeniedToAnonymous_with401() throws Exception {
            mockMvc.perform(get("/uploads/videos/probe.mp4"))
                    .andExpect(status().isUnauthorized());
        }

        @ParameterizedTest
        @CsvSource({"STUDENT", "TEACHER", "ADMIN"})
        void videoUploads_areDeniedToEveryRole_with403(String role) throws Exception {
            // denyAll: files must only be served through the VideoController
            call("GET", "/uploads/videos/probe.mp4", role).andExpect(status().isForbidden());
        }

        @Test
        void paddleWebhook_isNotBlockedByAuthentication() throws Exception {
            // No JWT, no cookie. Whatever the controller answers (e.g. invalid signature),
            // the security chain must not demand a login: the body must not be the
            // generic "unauthenticated" response of AuthEntryPoint.
            mockMvc.perform(post("/api/paddle/webhook")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                            .doesNotContain("Missing required request header"));
        }

        @Test
        void unknownApiPath_anonymous_returns401() throws Exception {
            // anyRequest().authenticated()
            call("GET", "/api/probe/unmapped", null).andExpect(status().isUnauthorized());
        }

        @ParameterizedTest
        @CsvSource({"STUDENT", "TEACHER", "ADMIN"})
        void unknownApiPath_authenticated_passesSecurity(String role) throws Exception {
            call("GET", "/api/probe/unmapped", role).andExpect(passedSecurity());
        }
    }

    // ---------------------------------------------------------------
    // /api/admin/**
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("/api/admin/** is ADMIN only")
    class AdminRules {

        @ParameterizedTest
        @CsvSource({"STUDENT", "TEACHER"})
        void nonAdmin_isForbidden(String role) throws Exception {
            call("GET", "/api/admin/users", role).andExpect(status().isForbidden());
            call("PUT", "/api/admin/users/1/role?role=ADMIN", role).andExpect(status().isForbidden());
            call("PUT", "/api/admin/users/1/deactivate", role).andExpect(status().isForbidden());
            call("PUT", "/api/admin/users/1/activate", role).andExpect(status().isForbidden());
        }

        @Test
        void admin_canListUsers() throws Exception {
            call("GET", "/api/admin/users", "ADMIN")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void anonymous_isUnauthorized_onEveryAdminEndpoint() throws Exception {
            call("PUT", "/api/admin/users/1/role?role=ADMIN", null).andExpect(status().isUnauthorized());
            call("PUT", "/api/admin/users/1/deactivate", null).andExpect(status().isUnauthorized());
            call("PUT", "/api/admin/users/1/activate", null).andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------
    // Staff rules (ADMIN + TEACHER)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Staff-only write endpoints (ADMIN, TEACHER)")
    class StaffRules {

        private static final String CASES = """
                POST,/api/videos/probe/unmapped
                PUT,/api/videos/probe/unmapped
                DELETE,/api/videos/probe/unmapped
                POST,/api/quests/probe/unmapped
                PUT,/api/quests/probe/unmapped
                POST,/api/problems/probe/unmapped
                PUT,/api/problems/probe/unmapped
                DELETE,/api/problems/probe/image/x
                """;

        @ParameterizedTest(name = "{0} {1} as ADMIN/TEACHER passes")
        @CsvSource(textBlock = CASES)
        void adminAndTeacher_passSecurity(String method, String path) throws Exception {
            call(method, path, "ADMIN").andExpect(passedSecurity());
            call(method, path, "TEACHER").andExpect(passedSecurity());
        }

        @ParameterizedTest(name = "{0} {1} as STUDENT is 403")
        @CsvSource(textBlock = CASES)
        void student_isForbidden(String method, String path) throws Exception {
            call(method, path, "STUDENT").andExpect(status().isForbidden());
        }

        @ParameterizedTest(name = "{0} {1} as anonymous is 401")
        @CsvSource(textBlock = CASES)
        void anonymous_isUnauthorized(String method, String path) throws Exception {
            call(method, path, null).andExpect(status().isUnauthorized());
        }

        @ParameterizedTest
        @CsvSource({"/api/videos/probe/unmapped", "/api/quests/probe/unmapped", "/api/problems/probe/unmapped"})
        void reading_isNotStaffOnly(String path) throws Exception {
            // GET falls through to anyRequest().authenticated()
            call("GET", path, "STUDENT").andExpect(passedSecurity());
        }
    }

    // ---------------------------------------------------------------
    // ADMIN-only deletes
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("DELETE on quests/problems is ADMIN only")
    class AdminOnlyDeletes {

        @ParameterizedTest
        @CsvSource({"/api/quests/probe/unmapped", "/api/problems/probe/unmapped"})
        void admin_passes(String path) throws Exception {
            call("DELETE", path, "ADMIN").andExpect(passedSecurity());
        }

        @ParameterizedTest
        @CsvSource({"/api/quests/probe/unmapped", "/api/problems/probe/unmapped"})
        void teacher_isForbidden(String path) throws Exception {
            call("DELETE", path, "TEACHER").andExpect(status().isForbidden());
        }

        @ParameterizedTest
        @CsvSource({"/api/quests/probe/unmapped", "/api/problems/probe/unmapped"})
        void student_isForbidden(String path) throws Exception {
            call("DELETE", path, "STUDENT").andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------
    // Student actions on problems
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Problem actions open to any logged-in user")
    class ProblemActions {

        @ParameterizedTest
        @CsvSource({
                "/api/problems/submit",
                "/api/problems/42/hint",
                "/api/problems/42/give-up"
        })
        void anonymous_isUnauthorized(String path) throws Exception {
            call("POST", path, null).andExpect(status().isUnauthorized());
        }

        @Test
        void hintAndGiveUp_areNotCoveredByTheStaffPostRule() throws Exception {
            // A STUDENT must not get 403 here (the /api/problems/** staff rule comes later).
            // Controllers may fail afterwards for other reasons (null principal), so only
            // the security decision is checked through the exception-safe 'unmapped' sibling:
            // /api/problems/probe/unmapped IS staff-only, hint/give-up are not.
            call("POST", "/api/problems/probe/unmapped", "STUDENT").andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------
    // CSRF header protection (CsrfHeaderFilter)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("CSRF header filter")
    class Csrf {

        @Test
        void unsafeMethod_withAccessCookie_andNoHeader_returns403() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .cookie(new Cookie(AuthCookieService.ACCESS, "x"))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Missing required request header"));
        }

        @Test
        void unsafeMethod_withRefreshCookie_andNoHeader_returns403() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .cookie(new Cookie(AuthCookieService.REFRESH, "x"))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Missing required request header"));
        }

        @Test
        void unsafeMethod_withCookie_andWrongHeaderValue_returns403() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .cookie(new Cookie(AuthCookieService.ACCESS, "x"))
                            .header("X-Requested-With", "fetch")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unsafeMethod_withCookie_andHeader_passesTheFilter() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .cookie(new Cookie(AuthCookieService.ACCESS, "x"))
                            .header("X-Requested-With", "XMLHttpRequest")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void unsafeMethod_withoutCookies_doesNotNeedTheHeader() throws Exception {
            // Bearer-token and anonymous clients are not subject to the cookie CSRF check
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void safeMethod_withCookie_andNoHeader_isNotBlockedByTheFilter() throws Exception {
            // GET is skipped by the filter: invalid cookie -> 401, never the CSRF 403
            mockMvc.perform(get("/api/admin/users")
                            .cookie(new Cookie(AuthCookieService.ACCESS, "x")))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------
    // CORS (CorsConfig)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("CORS")
    class Cors {

        @ParameterizedTest
        @CsvSource({"http://localhost:3000", "http://localhost:5173"})
        void preflight_fromAllowedOrigin_isAccepted(String origin) throws Exception {
            mockMvc.perform(options("/api/auth/login")
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "Content-Type,X-Requested-With"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }

        @Test
        void preflight_fromUnknownOrigin_isRejected() throws Exception {
            mockMvc.perform(options("/api/auth/login")
                            .header("Origin", "https://evil.example.com")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void preflight_withDisallowedHeader_isRejected() throws Exception {
            mockMvc.perform(options("/api/auth/login")
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "X-Evil-Header"))
                    .andExpect(status().isForbidden());
        }
    }
}