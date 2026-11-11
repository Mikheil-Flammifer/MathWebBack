package com.mathweb.service.impl;

import com.mathweb.service.EmailService;
import com.mathweb.util.SanitizationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail-from}")
    private String mailFrom;

    @Value("${app.name}")
    private String appName;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    @Override
    public void sendOtpEmail(String toEmail, String firstName,
                             String otp, String purpose) {
        String subject;
        String body;

        if ("EMAIL_VERIFICATION".equals(purpose)) {
            subject = appName + " - Verify Your Email";
            body = buildOtpEmailBody(firstName, otp,
                    "verify your email address",
                    "This code will expire in 10 minutes.");
        } else {
            subject = appName + " - Password Reset Code";
            body = buildOtpEmailBody(firstName, otp,
                    "reset your password",
                    "This code will expire in 10 minutes. If you did not request this, ignore this email.");
        }

        sendHtmlEmail(toEmail, subject, body);
    }

    @Async
    @Override
    public void sendWelcomeEmail(String toEmail, String firstName) {
        sendHtmlEmail(toEmail, "Welcome to " + appName + "!", welcomeBody(firstName));
    }

    @Async
    @Override
    public void sendSubscriptionConfirmationEmail(String toEmail, String firstName) {
        sendHtmlEmail(toEmail, appName + " - Access Activated!", subscriptionConfirmationBody(firstName));
    }

    @Async
    @Override
    public void sendSubscriptionCancelledEmail(String toEmail, String firstName) {
        sendHtmlEmail(toEmail, appName + " - Subscription Cancelled", subscriptionCancelledBody(firstName));
    }

    // ===== BODIES (package-private so tests can call them) =====

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

    String buildOtpEmailBody(String firstName, String otp,
                             String purpose, String note) {
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

    // ===== PRIVATE HELPERS =====

    /** User text must never reach an HTML body unescaped. */
    private static String escape(String text) {
        String safe = SanitizationUtil.escapeHtml(text);
        return safe == null ? "" : safe;
    }

    private void sendHtmlEmail(String toEmail, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            log.info("Email sent to: {}", toEmail);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }
}