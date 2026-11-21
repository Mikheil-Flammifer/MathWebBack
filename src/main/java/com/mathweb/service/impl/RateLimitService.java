package com.mathweb.service.impl;

import com.mathweb.exception.TooManyRequestsException;
import com.mathweb.util.TokenHashUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class RateLimitService {
    private static final int WINDOW_SECONDS = 15 * 60;

    private final JdbcTemplate jdbc;

    public RateLimitService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void checkOtpRate(String email) {
        checkBoth("otp", normalize(email), 3,
                "Too many OTP requests. Please wait 15 minutes.");
    }

    public void checkLoginRate(String email) {
        checkBoth("login", normalize(email), 5,
                "Too many login attempts. Please wait 15 minutes.");
    }

    public void checkIpRate(String action, int maxAttempts, String message) {
        check(action + ":ip", clientIp(), maxAttempts, message);
    }

    public void checkAccountRate(String action, Long accountId,
                                 int maxAttempts, String message) {
        check(action + ":account", String.valueOf(accountId),
                maxAttempts, message);
    }

    public void resetLoginRate(String email) {
        delete("login:account", normalize(email));
        delete("login:ip", clientIp());
    }

    public void cleanupExpiredEntries() {
        jdbc.update("""
                delete from rate_limit_entries
                where expires_at <= current_timestamp
                """);
    }

    public void checkOtpVerificationRate(String email) {
        checkBoth(
                "otp-verify",
                normalize(email),
                10,
                "Too many verification attempts. Please wait 15 minutes."
        );
    }

    public void checkPasswordResetRate(String email) {
        checkBoth(
                "password-reset",
                normalize(email),
                5,
                "Too many password-reset attempts. Please wait 15 minutes."
        );
    }

    private void checkBoth(String action, String account, int max,
                           String message) {
        check(action + ":account", account, max, message);

        // Higher IP limit avoids blocking a whole school/office behind NAT.
        check(action + ":ip", clientIp(), max * 4, message);
    }

    private void check(String scope, String subject, int max,
                       String message) {
        RateWindow window = jdbc.queryForObject("""
                insert into rate_limit_entries
                    (scope, subject_hash, window_start, expires_at, attempt_count)
                values (?, ?, current_timestamp,
                        current_timestamp + (? * interval '1 second'), 1)
                on conflict (scope, subject_hash) do update set
                    attempt_count = case
                        when rate_limit_entries.expires_at <= current_timestamp then 1
                        else rate_limit_entries.attempt_count + 1
                    end,
                    window_start = case
                        when rate_limit_entries.expires_at <= current_timestamp then current_timestamp
                        else rate_limit_entries.window_start
                    end,
                    expires_at = case
                        when rate_limit_entries.expires_at <= current_timestamp
                            then current_timestamp + (? * interval '1 second')
                        else rate_limit_entries.expires_at
                    end
                returning attempt_count,
                    greatest(1, extract(epoch from
                        (expires_at - current_timestamp))::bigint)
                """,
                (rs, rowNum) -> new RateWindow(rs.getInt(1), rs.getLong(2)),
                scope,
                TokenHashUtil.sha256(subject),
                WINDOW_SECONDS,
                WINDOW_SECONDS
        );

        if (window == null || window.attemptCount() > max) {
            throw new TooManyRequestsException(
                    message,
                    window == null ? WINDOW_SECONDS : window.retryAfterSeconds()
            );
        }
    }

    private void delete(String scope, String subject) {
        jdbc.update("""
                        delete from rate_limit_entries
                        where scope = ? and subject_hash = ?
                        """,
                scope,
                TokenHashUtil.sha256(subject)
        );
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();

            // Do not trust X-Forwarded-For directly.
            return request.getRemoteAddr();
        }
        return "no-request";
    }

    private record RateWindow(int attemptCount, long retryAfterSeconds) { }
}