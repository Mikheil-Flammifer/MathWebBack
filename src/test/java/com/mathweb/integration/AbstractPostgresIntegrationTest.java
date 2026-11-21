package com.mathweb.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
public abstract class AbstractPostgresIntegrationTest {

    // One container shared by every integration test class (singleton pattern).
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");

        // No context may run the background processor against the shared database
        // while tests are asserting on inbox rows.
        registry.add("paddle.processor.poll-ms", () -> "3600000");
        registry.add("paddle.reconciliation.initial-delay-ms", () -> "3600000");
        registry.add("paddle.reconciliation.poll-ms", () -> "3600000");
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearPersistentSecurityState() {
        jdbcTemplate.update("DELETE FROM rate_limit_entries");
        jdbcTemplate.update("DELETE FROM revoked_access_tokens");
    }
}