package com.mathweb.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupConfigValidatorTest {

    private static String base64OfBytes(int n) {
        return Base64.getEncoder().encodeToString(new byte[n]);
    }

    @Test
    void secretOf48Bytes_isAccepted() {
        assertThatCode(() -> StartupConfigValidator.validateJwtSecret(base64OfBytes(48)))
                .doesNotThrowAnyException();
    }

    @Test
    void secretOfExactly32Bytes_isAccepted() {
        assertThatCode(() -> StartupConfigValidator.validateJwtSecret(base64OfBytes(32)))
                .doesNotThrowAnyException();
    }

    @Test
    void secretShorterThan32Bytes_isRejected() {
        assertThatThrownBy(() -> StartupConfigValidator.validateJwtSecret(base64OfBytes(16)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void secretThatIsNotBase64_isRejected() {
        assertThatThrownBy(() -> StartupConfigValidator.validateJwtSecret("this is not base64!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingSecret_isRejected(String secret) {
        assertThatThrownBy(() -> StartupConfigValidator.validateJwtSecret(secret))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void httpsWithInsecureCookies_isRejected() {
        assertThatThrownBy(() -> StartupConfigValidator.validateCookies("https://api.example.com", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_PROFILES_ACTIVE=prod");
    }

    @Test
    void httpsWithSecureCookies_isAccepted() {
        assertThatCode(() -> StartupConfigValidator.validateCookies("https://api.example.com", true))
                .doesNotThrowAnyException();
    }

    @Test
    void localhostHttpWithInsecureCookies_isAccepted() {
        assertThatCode(() -> StartupConfigValidator.validateCookies("http://localhost:8080", false))
                .doesNotThrowAnyException();
    }
}