package com.mathweb.service.impl;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class TokenBlacklistService {

    // token -> expiry time
    private final Map<String, LocalDateTime> blacklistedTokens =
            new ConcurrentHashMap<>();

    public void blacklist(String token, LocalDateTime expiresAt) {
        blacklistedTokens.put(token, expiresAt);
    }

    public boolean isBlacklisted(String token) {
        if (!blacklistedTokens.containsKey(token)) return false;

        // Auto-remove expired tokens
        LocalDateTime expiry = blacklistedTokens.get(token);
        if (LocalDateTime.now().isAfter(expiry)) {
            blacklistedTokens.remove(token);
            return false;
        }
        return true;
    }

    // Called by scheduler to clean up
    public void removeExpiredTokens() {
        blacklistedTokens.entrySet()
                .removeIf(entry ->
                        LocalDateTime.now().isAfter(entry.getValue()));
    }
}