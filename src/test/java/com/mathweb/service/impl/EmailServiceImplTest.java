package com.mathweb.service.impl;

import com.mathweb.repository.EmailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmailServiceImplTest {

    private static final String XSS = "<script>alert(1)</script>";

    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        EmailOutboxRepository outboxRepository =
                mock(EmailOutboxRepository.class);

        EmailOutboxPayloadCipher payloadCipher =
                mock(EmailOutboxPayloadCipher.class);

        service = new EmailServiceImpl(
                outboxRepository,
                payloadCipher
        );

        ReflectionTestUtils.setField(service, "appName", "MathWeb");
    }

    @Test
    void welcomeBody_escapesTheName() {
        String body = service.welcomeBody(XSS);

        assertThat(body)
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    @Test
    void otpBody_escapesTheName_andKeepsTheCode() {
        String body = service.buildOtpEmailBody(
                XSS,
                "123456",
                "verify your email address",
                "note"
        );

        assertThat(body)
                .doesNotContain("<script>")
                .contains("&lt;script&gt;")
                .contains("123456");
    }

    @Test
    void confirmationAndCancelledBodies_escapeTheName() {
        assertThat(service.subscriptionConfirmationBody(XSS))
                .doesNotContain("<script>");

        assertThat(service.subscriptionCancelledBody(XSS))
                .doesNotContain("<script>");
    }

    @Test
    void nullName_doesNotBreakTheBody() {
        assertThat(service.welcomeBody(null))
                .contains("Welcome to MathWeb");
    }

    @Test
    void noBodyMentionsTheOldMonthlyPrice() {
        assertThat(service.welcomeBody("Nino"))
                .doesNotContain("2.49");

        assertThat(service.subscriptionConfirmationBody("Nino"))
                .doesNotContain("2.49");
    }
}