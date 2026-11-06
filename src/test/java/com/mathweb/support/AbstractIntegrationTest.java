package com.mathweb.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for integration tests (class names end in IT).
 * Boots the full application against a real PostgreSQL with all Flyway migrations applied,
 * using src/test/resources/application-test.yml. E-mail sending is replaced by a mock
 * (MockEmailConfig), so no SMTP server is needed.
 *
 * Keep the annotations on this class identical for all subclasses: Spring reuses one
 * application context for the whole test run only while the configuration is the same.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, MockEmailConfig.class})
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;
}