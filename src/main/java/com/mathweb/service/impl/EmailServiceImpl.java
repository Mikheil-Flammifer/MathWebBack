package com.mathweb.service.impl;

import com.mathweb.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
        String subject = "Welcome to " + appName + "!";
        String body = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #4F46E5;">Welcome to %s, %s! 🎉</h2>
                    <p>Your email has been verified successfully.</p>
                    <p>You can now log in and start your math learning journey!</p>
                    <p>To unlock all features, consider subscribing for just <strong>$2.49/month</strong>.</p>
                    <br/>
                    <p>Happy learning!</p>
                    <p><strong>The %s Team</strong></p>
                </div>
                """.formatted(appName, firstName, appName);

        sendHtmlEmail(toEmail, subject, body);
    }

    @Async
    @Override
    public void sendSubscriptionConfirmationEmail(String toEmail, String firstName) {
        String subject = appName + " - Subscription Activated!";
        String body = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #4F46E5;">Subscription Activated, %s! 🚀</h2>
                    <p>Your subscription to %s has been activated successfully.</p>
                    <p>You now have full access to all videos, quests, and problems.</p>
                    <p>Amount: <strong>$2.49/month</strong></p>
                    <br/>
                    <p>Start learning today!</p>
                    <p><strong>The %s Team</strong></p>
                </div>
                """.formatted(firstName, appName, appName);

        sendHtmlEmail(toEmail, subject, body);
    }

    @Async
    @Override
    public void sendSubscriptionCancelledEmail(String toEmail, String firstName) {
        String subject = appName + " - Subscription Cancelled";
        String body = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #4F46E5;">Subscription Cancelled</h2>
                    <p>Hi %s,</p>
                    <p>Your subscription to %s has been cancelled.</p>
                    <p>You will continue to have access until the end of your current billing period.</p>
                    <p>We hope to see you back soon!</p>
                    <br/>
                    <p><strong>The %s Team</strong></p>
                </div>
                """.formatted(firstName, appName, appName);

        sendHtmlEmail(toEmail, subject, body);
    }

    // ===== PRIVATE HELPERS =====

    private String buildOtpEmailBody(String firstName, String otp,
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
                """.formatted(appName, firstName, purpose, otp, note, appName);
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
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }
}