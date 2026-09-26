package com.mathweb.util;

import java.security.SecureRandom;

public class OtpUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_LENGTH = 6;

    private OtpUtil() {}

    public static String generateOtp() {
        int min = (int) Math.pow(10, OTP_LENGTH - 1);
        int max = (int) Math.pow(10, OTP_LENGTH) - 1;
        return String.valueOf(min + RANDOM.nextInt(max - min + 1));
    }

    public static boolean isValidFormat(String otp) {
        if (otp == null) return false;
        return otp.matches("\\d{" + OTP_LENGTH + "}");
    }
}