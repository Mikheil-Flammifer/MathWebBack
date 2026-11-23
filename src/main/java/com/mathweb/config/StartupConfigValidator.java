package com.mathweb.config;

import io.jsonwebtoken.io.Decoders;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StartupConfigValidator {

    private static final int MIN_JWT_KEY_BYTES = 32;

    private final String jwtSecret;
    private final String baseUrl;
    private final boolean cookiesSecure;
    private final String databaseUrl;
    private final boolean databaseTlsRequired;

    public StartupConfigValidator(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${app.base-url}") String baseUrl,
            @Value("${app.cookies.secure:true}") boolean cookiesSecure,
            @Value("${spring.datasource.url:}") String databaseUrl,
            @Value("${app.database.require-tls:false}") boolean databaseTlsRequired
    ) {
        this.jwtSecret = jwtSecret;
        this.baseUrl = baseUrl;
        this.cookiesSecure = cookiesSecure;
        this.databaseUrl = databaseUrl;
        this.databaseTlsRequired = databaseTlsRequired;
    }

    @PostConstruct
    void validate() {
        validateJwtSecret(jwtSecret);
        validateCookies(baseUrl, cookiesSecure);
        validateDatabaseTls(databaseUrl, databaseTlsRequired);
    }

    static void validateJwtSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is missing");
        }

        byte[] key;

        try {
            key = Decoders.BASE64.decode(secret);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET must be valid Base64 "
                            + "(generate one with: openssl rand -base64 48)"
            );
        }

        if (key.length < MIN_JWT_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short: it must decode to at least "
                            + "32 bytes (got " + key.length + ")"
            );
        }
    }

    static void validateCookies(String baseUrl, boolean cookiesSecure) {
        if (baseUrl != null
                && baseUrl.trim().toLowerCase().startsWith("https://")
                && !cookiesSecure) {
            throw new IllegalStateException(
                    "app.base-url is https but app.cookies.secure is false. "
                            + "The 'prod' profile is probably not active: "
                            + "set SPRING_PROFILES_ACTIVE=prod"
            );
        }
    }

    static void validateDatabaseTls(String databaseUrl, boolean required) {
        if (!required) {
            return;
        }

        if (databaseUrl == null
                || !databaseUrl.toLowerCase(java.util.Locale.ROOT)
                .contains("sslmode=verify-full")) {
            throw new IllegalStateException(
                    "DB_URL must include sslmode=verify-full in production"
            );
        }
    }
}