package com.mathweb.service.impl;

import com.mathweb.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for {@link RateLimitService}. */
class RateLimitServiceTest {

    private static final String EMAIL = "student@example.com";

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService();
    }

    @SuppressWarnings("unchecked")
    private Map<String, LocalDateTime> windows() {
        return (Map<String, LocalDateTime>) ReflectionTestUtils.getField(service, "windowStart");
    }

    @SuppressWarnings("unchecked")
    private Map<String, int[]> attempts() {
        return (Map<String, int[]>) ReflectionTestUtils.getField(service, "attempts");
    }

    @Test
    void checkOtpRate_allowsThreeRequests_thenRejectsTheFourth() {
        for (int i = 0; i < 3; i++) {
            service.checkOtpRate(EMAIL);
        }

        assertThatThrownBy(() -> service.checkOtpRate(EMAIL))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Too many OTP requests. Please wait 15 minutes.");
    }

    @Test
    void checkLoginRate_allowsFiveAttempts_thenRejectsTheSixth() {
        for (int i = 0; i < 5; i++) {
            service.checkLoginRate(EMAIL);
        }

        assertThatThrownBy(() -> service.checkLoginRate(EMAIL))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Too many login attempts. Please wait 15 minutes.");
    }

    @Test
    void otpAndLoginLimits_areTrackedIndependently() {
        for (int i = 0; i < 3; i++) {
            service.checkOtpRate(EMAIL);
        }

        assertThatCode(() -> service.checkLoginRate(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void limits_areTrackedPerEmail() {
        for (int i = 0; i < 3; i++) {
            service.checkOtpRate(EMAIL);
        }

        assertThatCode(() -> service.checkOtpRate("other@example.com")).doesNotThrowAnyException();
    }

    @Test
    void resetLoginRate_clearsTheCounter() {
        for (int i = 0; i < 5; i++) {
            service.checkLoginRate(EMAIL);
        }

        service.resetLoginRate(EMAIL);

        assertThatCode(() -> service.checkLoginRate(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void resetLoginRate_doesNotAffectOtpCounter() {
        for (int i = 0; i < 3; i++) {
            service.checkOtpRate(EMAIL);
        }

        service.resetLoginRate(EMAIL);

        assertThatThrownBy(() -> service.checkOtpRate(EMAIL))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void checkRate_startsNewWindow_whenPreviousOneExpired() {
        for (int i = 0; i < 3; i++) {
            service.checkOtpRate(EMAIL);
        }
        windows().put("otp:" + EMAIL, LocalDateTime.now().minusMinutes(16));

        assertThatCode(() -> service.checkOtpRate(EMAIL)).doesNotThrowAnyException();
        assertThat(attempts().get("otp:" + EMAIL)[0]).isEqualTo(1);
    }

    @Test
    void cleanupExpiredEntries_removesExpiredWindowsAndTheirCounters() {
        service.checkOtpRate(EMAIL);
        service.checkLoginRate(EMAIL);
        windows().put("otp:" + EMAIL, LocalDateTime.now().minusMinutes(16));

        service.cleanupExpiredEntries();

        assertThat(windows()).doesNotContainKey("otp:" + EMAIL).containsKey("login:" + EMAIL);
        assertThat(attempts()).doesNotContainKey("otp:" + EMAIL).containsKey("login:" + EMAIL);
    }
}