package com.mathweb.service.impl;

import com.mathweb.entity.EmailOutbox;
import com.mathweb.enums.EmailDeliveryStatus;
import com.mathweb.repository.EmailOutboxRepository;
import com.mathweb.service.EmailService;
import com.mathweb.util.SanitizationUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class EmailServiceImpl implements EmailService {

    private final EmailOutboxRepository outboxRepository;
    private final EmailOutboxPayloadCipher payloadCipher;

    @Value("${app.name}")
    private String appName;

    @Value("${otp.expiration-minutes}")
    private long otpExpirationMinutes;

    public EmailServiceImpl(
            EmailOutboxRepository outboxRepository,
            EmailOutboxPayloadCipher payloadCipher
    ) {
        this.outboxRepository = outboxRepository;
        this.payloadCipher = payloadCipher;
    }

    @Override
    public void sendOtpEmail(
            String toEmail,
            String firstName,
            String otp,
            String purpose
    ) {
        String subject;
        String body;

        if ("EMAIL_VERIFICATION".equals(purpose)) {
            subject = appName + " - Verify Your Email";
            body = buildOtpEmailBody(
                    firstName,
                    otp,
                    "verify your email address",
                    "This code will expire in 10 minutes."
            );
        } else {
            subject = appName + " - Password Reset Code";
            body = buildOtpEmailBody(
                    firstName,
                    otp,
                    "reset your password",
                    "This code will expire in 10 minutes. "
                            + "If you did not request this, ignore this email."
            );
        }

        sendHtmlEmail(
                toEmail,
                subject,
                body,
                Instant.now().plusSeconds(otpExpirationMinutes * 60)
        );
    }

    @Override
    public void sendWelcomeEmail(String toEmail, String firstName) {
        sendHtmlEmail(
                toEmail,
                "Welcome to " + appName + "!",
                welcomeBody(firstName),
                null
        );
    }

    @Override
    public void sendSubscriptionConfirmationEmail(
            String toEmail,
            String firstName
    ) {
        sendHtmlEmail(
                toEmail,
                appName + " - Access Activated!",
                subscriptionConfirmationBody(firstName),
                null
        );
    }

    @Override
    public void sendSubscriptionCancelledEmail(
            String toEmail,
            String firstName
    ) {
        sendHtmlEmail(
                toEmail,
                appName + " - Subscription Cancelled",
                subscriptionCancelledBody(firstName),
                null
        );
    }

    String welcomeBody(String firstName) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #4F46E5;">Welcome to %s, %s! 🎉</h2>
                    <p>Your email has been verified successfully.</p>
                    <p>You can now log in and start your math learning journey!</p>
                    <p>To unlock all videos, choose a 30-day or 90-day pass in your account.</p>
                    <br/>
                    <p>Happy learning!</p>
                    <p><strong>The %s Team</strong></p>
                </div>
                """.formatted(appName, escape(firstName), appName);
    }

    String subscriptionConfirmationBody(String firstName) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #4F46E5;">Access Activated, %s! 🚀</h2>
                    <p>Your access to %s has been activated successfully.</p>
                    <p>You now have full access to all videos, quests, and problems.</p>
                    <br/>
                    <p>Start learning today!</p>
                    <p><strong>The %s Team</strong></p>
                </div>
                """.formatted(escape(firstName), appName, appName);
    }

    String subscriptionCancelledBody(String firstName) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #4F46E5;">Subscription Cancelled</h2>
                    <p>Hi %s,</p>
                    <p>Your access to %s has been cancelled.</p>
                    <p>We hope to see you back soon!</p>
                    <br/>
                    <p><strong>The %s Team</strong></p>
                </div>
                """.formatted(escape(firstName), appName, appName);
    }

    String buildOtpEmailBody(
            String firstName,
            String otp,
            String purpose,
            String note
    ) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #4F46E5;">%s</h2>
                    <p>Hi %s,</p>
                    <p>Use the following code to %s:</p>
                    <div style="background: #F3F4F6; padding: 20px; text-align: center;
                                border-radius: 8px; margin: 20px 0;">
                        <h1 style="color: #4F46E5; font-size: 48px; letter-spacing: 8px;
                                   margin: 0;">%s</h1>
                    </div>
                    <p style="color: #6B7280; font-size: 14px;">%s</p>
                    <br/>
                    <p><strong>The %s Team</strong></p>
                </div>
                """.formatted(appName, escape(firstName), purpose, otp, note, appName);
    }

    private static String escape(String text) {
        String safe = SanitizationUtil.escapeHtml(text);
        return safe == null ? "" : safe;
    }

    private void sendHtmlEmail(
            String toEmail,
            String subject,
            String body,
            Instant expiresAt
    ) {
        EmailOutbox outbox = new EmailOutbox();

        outbox.setPayload(payloadCipher.encrypt(
                new EmailOutboxPayloadCipher.EmailPayload(
                        toEmail,
                        subject,
                        body,
                        expiresAt
                )
        ));

        outbox.setStatus(EmailDeliveryStatus.PENDING);
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(Instant.now());
        outbox.setCreatedAt(Instant.now());

        outboxRepository.save(outbox);
    }
}