package com.mathweb.service;

public interface EmailService {
    void sendOtpEmail(String toEmail, String firstName, String otp, String purpose);
    void sendWelcomeEmail(String toEmail, String firstName);
    void sendSubscriptionConfirmationEmail(String toEmail, String firstName);
    void sendSubscriptionCancelledEmail(String toEmail, String firstName);
}