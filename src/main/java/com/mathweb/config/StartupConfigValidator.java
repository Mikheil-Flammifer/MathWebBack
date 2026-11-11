package com.mathweb.config;

import io.jsonwebtoken.io.Decoders;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fails the application at startup, with a clear message, instead of failing
 * later on the first login or silently running with unsafe settings.
 */
@Component
public class StartupConfigValidator {

    private static final int MIN_JWT_KEY_BYTES = 32;   // 256 bits for HS256

    private final String jwtSecret;
    private final String baseUrl;
    private final boolean cookiesSecure;

    public StartupConfigValidator(@Value("${jwt.secret}") String jwtSecret,
                                  @Value("${app.base-url}") String baseUrl,
                                  @Value("${app.cookies.secure:true}") boolean cookiesSecure) {
        this.jwtSecret = jwtSecret;
        this.baseUrl = baseUrl;
        this.cookiesSecure = cookiesSecure;
    }

    @PostConstruct
    void validate() {
        validateJwtSecret(jwtSecret);
        validateCookies(baseUrl, cookiesSecure);
    }

    /** JwtTokenProvider Base64-decodes the secret, so it must be valid Base64 of at least 32 bytes. */
    static void validateJwtSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is missing");
        }
        byte[] key;
        try {
            key = Decoders.BASE64.decode(secret);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "JWT_SECRET must be valid Base64 (generate one with: openssl rand -base64 48)");
        }
        if (key.length < MIN_JWT_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short: it must decode to at least 32 bytes (got " + key.length + ")");
        }
    }

    /** A public https site must never hand out cookies without the Secure flag. */
    static void validateCookies(String baseUrl, boolean cookiesSecure) {
        if (baseUrl != null && baseUrl.trim().toLowerCase().startsWith("https://") && !cookiesSecure) {
            throw new IllegalStateException(
                    "app.base-url is https but app.cookies.secure is false. "
                            + "The 'prod' profile is probably not active: set SPRING_PROFILES_ACTIVE=prod");
        }
    }
}