package com.mathweb.service.impl;

import com.mathweb.entity.RevokedAccessToken;
import com.mathweb.repository.RevokedAccessTokenRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TokenBlacklistService {

    private final RevokedAccessTokenRepository repository;

    public TokenBlacklistService(RevokedAccessTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void blacklist(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()
                || expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            return;
        }

        try {
            repository.save(RevokedAccessToken.builder()
                    .jti(jti)
                    .expiresAt(expiresAt)
                    .build());
        } catch (DataIntegrityViolationException ignored) {
            // Logout is idempotent: this token was already revoked.
        }
    }

    @Transactional(readOnly = true)
    public boolean isBlacklisted(String jti) {
        return jti != null
                && repository.existsByJtiAndExpiresAtAfter(jti, Instant.now());
    }

    @Transactional
    public void removeExpiredTokens() {
        repository.deleteByExpiresAtBefore(Instant.now());
    }
}