package com.mathweb.service.impl;

import com.mathweb.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    // key -> [attemptCount, windowStart]
    private final Map<String, int[]> attempts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> windowStart = new ConcurrentHashMap<>();

    private static final int MAX_OTP_REQUESTS = 3;       // 3 OTPs per window
    private static final int MAX_LOGIN_ATTEMPTS = 5;      // 5 logins per window
    private static final int WINDOW_MINUTES = 15;         // per 15 minutes

    public void checkOtpRate(String email) {
        checkRate("otp:" + email, MAX_OTP_REQUESTS,
                "Too many OTP requests. Please wait 15 minutes.");
    }

    public void checkLoginRate(String email) {
        checkRate("login:" + email, MAX_LOGIN_ATTEMPTS,
                "Too many login attempts. Please wait 15 minutes.");
    }

    public void resetLoginRate(String email) {
        attempts.remove("login:" + email);
        windowStart.remove("login:" + email);
    }

    private void checkRate(String key, int maxAttempts, String message) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = windowStart.get(key);

        // Reset window if expired
        if (start == null || now.isAfter(start.plusMinutes(WINDOW_MINUTES))) {
            windowStart.put(key, now);
            attempts.put(key, new int[]{1});
            return;
        }

        int[] count = attempts.getOrDefault(key, new int[]{0});
        count[0]++;
        attempts.put(key, count);

        if (count[0] > maxAttempts) {
            throw new BadRequestException(message);
        }
    }

    // Called by scheduler
    public void cleanupExpiredEntries() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusMinutes(WINDOW_MINUTES);
        windowStart.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
        attempts.keySet().removeIf(k -> !windowStart.containsKey(k));
    }
}