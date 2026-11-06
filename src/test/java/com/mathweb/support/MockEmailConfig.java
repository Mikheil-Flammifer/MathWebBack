package com.mathweb.support;

import com.mathweb.service.EmailService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the real EmailService in integration tests so nothing tries to reach an SMTP server
 * (no "Connection refused" noise, no real e-mails). Tests read OTP codes from the database.
 *
 * Location: src/test/java/com/mathweb/support/MockEmailConfig.java
 */
@TestConfiguration
public class MockEmailConfig {

    @Bean
    @Primary
    public EmailService emailService() {
        return Mockito.mock(EmailService.class);
    }
}