package com.mathweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OtpUtilTest {

    @Test
    void testGenerateOtp_Always6Digits() {
        String otp = OtpUtil.generateOtp();
        assertEquals(6, otp.length());
        assertTrue(otp.matches("\\d{6}"));
    }

    @Test
    void testGenerateOtp_NeverNull() {
        String otp = OtpUtil.generateOtp();
        assertNotNull(otp);
    }

    @Test
    void testGenerateOtp_NumericOnly() {
        String otp = OtpUtil.generateOtp();
        for (int i = 0; i < otp.length(); i++) {
            assertTrue(Character.isDigit(otp.charAt(i)));
        }
    }

    @Test
    void testGenerateOtp_NoLeadingZeros() {
        for (int i = 0; i < 100; i++) {
            String otp = OtpUtil.generateOtp();
            assertNotEquals('0', otp.charAt(0));
        }
    }

    @Test
    void testGenerateOtp_Randomness() {
        String first = OtpUtil.generateOtp();
        boolean allSame = true;
        for (int i = 0; i < 10; i++) {
            if (!first.equals(OtpUtil.generateOtp())) {
                allSame = false;
                break;
            }
        }
        assertFalse(allSame, "OTPs should be random and different");
    }

    @Test
    void testIsValidFormat_Valid6DigitOtp() {
        assertTrue(OtpUtil.isValidFormat("123456"));
        assertTrue(OtpUtil.isValidFormat("000000"));
        assertTrue(OtpUtil.isValidFormat("999999"));
        assertTrue(OtpUtil.isValidFormat("111222"));
    }

    @Test
    void testIsValidFormat_InvalidLength() {
        assertFalse(OtpUtil.isValidFormat("12345"));
        assertFalse(OtpUtil.isValidFormat("1234567"));
        assertFalse(OtpUtil.isValidFormat("12"));
        assertFalse(OtpUtil.isValidFormat(""));
    }

    @Test
    void testIsValidFormat_NullInput() {
        assertFalse(OtpUtil.isValidFormat(null));
    }

    @Test
    void testIsValidFormat_NonNumeric() {
        assertFalse(OtpUtil.isValidFormat("abc123"));
        assertFalse(OtpUtil.isValidFormat("123abc"));
        assertFalse(OtpUtil.isValidFormat("12-34-56"));
        assertFalse(OtpUtil.isValidFormat("12 34 56"));
        assertFalse(OtpUtil.isValidFormat("12345a"));
    }

    @Test
    void testIsValidFormat_WithSpecialCharacters() {
        assertFalse(OtpUtil.isValidFormat("12345\n"));
        assertFalse(OtpUtil.isValidFormat("12345\t"));
        assertFalse(OtpUtil.isValidFormat("12345 "));
    }

    @Test
    void testGenerateAndValidate() {
        String otp = OtpUtil.generateOtp();
        assertTrue(OtpUtil.isValidFormat(otp));
    }
}