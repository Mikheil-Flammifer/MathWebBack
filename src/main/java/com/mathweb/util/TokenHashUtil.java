package com.mathweb.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TokenHashUtil {

    private TokenHashUtil() {
    }

    // Suitable for high-entropy bearer values such as refresh JWTs.
    public static String sha256(String value) {
        if (value == null) {
            return null;
        }

        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    // Suitable for short, guessable values such as six-digit OTPs.
    public static String hmacSha256(String pepper, String value) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException("OTP pepper is missing");
        }
        if (value == null) {
            return null;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    pepper.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));

            return HexFormat.of().formatHex(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", e);
        }
    }
}