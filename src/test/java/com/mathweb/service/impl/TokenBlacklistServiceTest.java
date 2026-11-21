package com.mathweb.service.impl;

import com.mathweb.entity.RevokedAccessToken;
import com.mathweb.repository.RevokedAccessTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private RevokedAccessTokenRepository repository;

    @Test
    void isBlacklisted_returnsFalse_forUnknownToken() {
        TokenBlacklistService service = new TokenBlacklistService(repository);

        assertThat(service.isBlacklisted("unknown")).isFalse();
    }

    @Test
    void blacklist_persistsJtiAndExpiry() {
        TokenBlacklistService service = new TokenBlacklistService(repository);
        Instant expiry = Instant.now().plusSeconds(600);

        service.blacklist("token-jti", expiry);

        ArgumentCaptor<RevokedAccessToken> captor =
                ArgumentCaptor.forClass(RevokedAccessToken.class);

        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getJti()).isEqualTo("token-jti");
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(expiry);
    }

    @Test
    void isBlacklisted_returnsTrue_forUnexpiredDatabaseRecord() {
        TokenBlacklistService service = new TokenBlacklistService(repository);

        when(repository.existsByJtiAndExpiresAtAfter(
                eq("token-jti"),
                any(Instant.class)
        )).thenReturn(true);

        assertThat(service.isBlacklisted("token-jti")).isTrue();
    }

    @Test
    void removeExpiredTokens_deletesExpiredDatabaseRows() {
        TokenBlacklistService service = new TokenBlacklistService(repository);

        service.removeExpiredTokens();

        verify(repository).deleteByExpiresAtBefore(any(Instant.class));
    }
}