package com.mathweb.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

@Component
class EmailOutboxPayloadCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final ObjectMapper objectMapper;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    EmailOutboxPayloadCipher(
            ObjectMapper objectMapper,
            @Value("${app.mail.outbox-encryption-key}") String encodedKey
    ) {
        this.objectMapper = objectMapper;
        this.key = new SecretKeySpec(decodeKey(encodedKey), "AES");
    }

    byte[] encrypt(EmailPayload payload) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv)
            );

            byte[] ciphertext =
                    cipher.doFinal(objectMapper.writeValueAsBytes(payload));

            byte[] result = Arrays.copyOf(
                    iv,
                    iv.length + ciphertext.length
            );

            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return result;
        } catch (GeneralSecurityException | JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not encrypt e-mail outbox payload",
                    exception
            );
        }
    }

    EmailPayload decrypt(byte[] encryptedPayload) {
        if (encryptedPayload == null || encryptedPayload.length <= IV_LENGTH) {
            throw new IllegalStateException("Invalid e-mail outbox payload");
        }

        try {
            byte[] iv = Arrays.copyOfRange(encryptedPayload, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(
                    encryptedPayload,
                    IV_LENGTH,
                    encryptedPayload.length
            );

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv)
            );

            return objectMapper.readValue(
                    cipher.doFinal(ciphertext),
                    EmailPayload.class
            );
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException(
                    "Could not decrypt e-mail outbox payload",
                    exception
            );
        }
    }

    private static byte[] decodeKey(String encodedKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey);

            if (decoded.length != 32) {
                throw new IllegalStateException(
                        "MAIL_OUTBOX_ENCRYPTION_KEY must decode to exactly 32 bytes"
                );
            }

            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "MAIL_OUTBOX_ENCRYPTION_KEY must be Base64",
                    exception
            );
        }
    }

    record EmailPayload(
            String toEmail,
            String subject,
            String htmlBody,
            Instant expiresAt
    ) {
    }
}