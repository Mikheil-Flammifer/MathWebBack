package com.mathweb.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailOutboxPayloadCipherTest {

    private static final String KEY =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final EmailOutboxPayloadCipher cipher =
            new EmailOutboxPayloadCipher(new ObjectMapper(), KEY);

    @Test
    void encryptsOtpPayloadAndRoundTripsIt() {
        EmailOutboxPayloadCipher.EmailPayload payload =
                new EmailOutboxPayloadCipher.EmailPayload(
                        "student@example.com",
                        "Verify",
                        "Your code is 123456",
                        null
                );

        byte[] encrypted = cipher.encrypt(payload);

        assertThat(new String(
                encrypted,
                java.nio.charset.StandardCharsets.UTF_8
        ))
                .doesNotContain("123456")
                .doesNotContain("student@example.com");

        assertThat(cipher.decrypt(encrypted)).isEqualTo(payload);
    }

    @Test
    void rejectsAnInvalidKey() {
        assertThatThrownBy(() ->
                new EmailOutboxPayloadCipher(
                        new ObjectMapper(),
                        "not-base64"
                )
        ).isInstanceOf(IllegalStateException.class);
    }
}