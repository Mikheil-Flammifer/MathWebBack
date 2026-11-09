package com.mathweb.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link TokenBlacklistService}. */
class TokenBlacklistServiceTest {

    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new TokenBlacklistService();
    }

    @Test
    void isBlacklisted_returnsFalse_forUnknownToken() {
        assertThat(service.isBlacklisted("unknown")).isFalse();
    }

    @Test
    void isBlacklisted_returnsTrue_beforeExpiry() {
        service.blacklist("token", LocalDateTime.now().plusMinutes(10));

        assertThat(service.isBlacklisted("token")).isTrue();
    }

    @Test
    void isBlacklisted_returnsFalse_afterExpiry() {
        service.blacklist("token", LocalDateTime.now().minusSeconds(1));

        assertThat(service.isBlacklisted("token")).isFalse();
        // second call still false (entry was removed)
        assertThat(service.isBlacklisted("token")).isFalse();
    }

    @Test
    void blacklist_doesNotAffectOtherTokens() {
        service.blacklist("a", LocalDateTime.now().plusMinutes(10));

        assertThat(service.isBlacklisted("b")).isFalse();
    }

    @Test
    void blacklist_overwritesExpiry_forSameToken() {
        service.blacklist("token", LocalDateTime.now().minusMinutes(1));
        service.blacklist("token", LocalDateTime.now().plusMinutes(10));

        assertThat(service.isBlacklisted("token")).isTrue();
    }

    @Test
    void removeExpiredTokens_removesOnlyExpired() {
        service.blacklist("expired", LocalDateTime.now().minusMinutes(1));
        service.blacklist("valid", LocalDateTime.now().plusMinutes(10));

        service.removeExpiredTokens();

        assertThat(service.isBlacklisted("expired")).isFalse();
        assertThat(service.isBlacklisted("valid")).isTrue();
    }
}