package com.mathweb.dto;

import com.mathweb.dto.request.LoginRequest;
import com.mathweb.dto.request.RegisterRequest;
import com.mathweb.dto.request.ResendOtpRequest;
import com.mathweb.dto.request.UpdateProfileRequest;
import com.mathweb.dto.request.VerifyOtpRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test of the bean-validation rules on request DTOs.
 * No Spring context, no database, no Docker.
 */
class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static <T> Set<String> fieldsWithErrors(T target) {
        return validator.validate(target).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    // ===== helpers =====

    private static RegisterRequest validRegister() {
        RegisterRequest r = new RegisterRequest();
        r.setFirstName("Nino");
        r.setLastName("Beridze");
        r.setEmail("nino@example.com");
        r.setPassword("Password123");
        return r;
    }

    private static LoginRequest validLogin() {
        LoginRequest r = new LoginRequest();
        r.setEmail("nino@example.com");
        r.setPassword("Password123");
        return r;
    }

    private static VerifyOtpRequest validVerify() {
        VerifyOtpRequest r = new VerifyOtpRequest();
        r.setEmail("nino@example.com");
        r.setCode("123456");
        r.setPurpose("EMAIL_VERIFICATION");
        return r;
    }

    private static ResendOtpRequest validResend() {
        ResendOtpRequest r = new ResendOtpRequest();
        r.setEmail("nino@example.com");
        r.setPurpose("PASSWORD_RESET");
        return r;
    }

    // ===== register =====

    @Test
    void register_valid_hasNoViolations() {
        assertThat(validator.validate(validRegister())).isEmpty();
    }

    @Test
    void register_emptyBody_flagsEveryRequiredField() {
        assertThat(fieldsWithErrors(new RegisterRequest()))
                .containsExactlyInAnyOrder("firstName", "lastName", "email", "password");
    }

    @Test
    void register_shortPassword_isRejected() {
        RegisterRequest r = validRegister();
        r.setPassword("short12");   // 7 chars
        assertThat(fieldsWithErrors(r)).containsExactly("password");
    }

    @Test
    void register_passwordOfExactlyEightChars_isAccepted() {
        RegisterRequest r = validRegister();
        r.setPassword("12345678");
        assertThat(validator.validate(r)).isEmpty();
    }

    @Test
    void register_passwordOver100Chars_isRejected() {
        RegisterRequest r = validRegister();
        r.setPassword("a".repeat(101));
        assertThat(fieldsWithErrors(r)).containsExactly("password");
    }

    @Test
    void register_singleLetterName_isRejected() {
        RegisterRequest r = validRegister();
        r.setFirstName("N");
        assertThat(fieldsWithErrors(r)).containsExactly("firstName");
    }

    @Test
    void register_emailOver255Chars_isRejected() {
        RegisterRequest r = validRegister();
        r.setEmail("a".repeat(250) + "@x.com");
        assertThat(fieldsWithErrors(r)).contains("email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "missing-at.example.com", "@no-local.com"})
    void register_invalidEmail_isRejected(String email) {
        RegisterRequest r = validRegister();
        r.setEmail(email);
        assertThat(fieldsWithErrors(r)).containsExactly("email");
    }

    // ===== login =====

    @Test
    void login_valid_hasNoViolations() {
        assertThat(validator.validate(validLogin())).isEmpty();
    }

    @Test
    void login_blankPassword_isRejected() {
        LoginRequest r = validLogin();
        r.setPassword("   ");
        assertThat(fieldsWithErrors(r)).containsExactly("password");
    }

    @Test
    void login_missingEmail_isRejected() {
        LoginRequest r = validLogin();
        r.setEmail(null);
        assertThat(fieldsWithErrors(r)).containsExactly("email");
    }

    @Test
    void login_passwordOver100Chars_isRejected() {
        LoginRequest r = validLogin();
        r.setPassword("a".repeat(101));
        assertThat(fieldsWithErrors(r)).containsExactly("password");
    }

    // ===== verify OTP =====

    @Test
    void verifyOtp_valid_hasNoViolations() {
        assertThat(validator.validate(validVerify())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567", "abcdef", "12 456", "12345a"})
    void verifyOtp_codeThatIsNotSixDigits_isRejected(String code) {
        VerifyOtpRequest r = validVerify();
        r.setCode(code);
        assertThat(fieldsWithErrors(r)).containsExactly("code");
    }

    @Test
    void verifyOtp_unknownPurpose_isRejected() {
        VerifyOtpRequest r = validVerify();
        r.setPurpose("LOGIN");
        assertThat(fieldsWithErrors(r)).containsExactly("purpose");
    }

    @Test
    void verifyOtp_bothPurposesAreAccepted() {
        VerifyOtpRequest r = validVerify();
        r.setPurpose("PASSWORD_RESET");
        assertThat(validator.validate(r)).isEmpty();
    }

    @Test
    void verifyOtp_purposeIsCaseSensitive() {
        VerifyOtpRequest r = validVerify();
        r.setPurpose("email_verification");
        assertThat(fieldsWithErrors(r)).containsExactly("purpose");
    }

    // ===== resend OTP =====

    @Test
    void resendOtp_valid_hasNoViolations() {
        assertThat(validator.validate(validResend())).isEmpty();
    }

    @Test
    void resendOtp_missingPurpose_isRejected() {
        ResendOtpRequest r = validResend();
        r.setPurpose(null);
        assertThat(fieldsWithErrors(r)).containsExactly("purpose");
    }

    @Test
    void resendOtp_unknownPurpose_isRejected() {
        ResendOtpRequest r = validResend();
        r.setPurpose("ANYTHING");
        assertThat(fieldsWithErrors(r)).containsExactly("purpose");
    }

    // ===== update profile =====

    @Test
    void updateProfile_emptyBody_isValid_becauseFieldsAreOptional() {
        assertThat(validator.validate(new UpdateProfileRequest())).isEmpty();
    }

    @Test
    void updateProfile_tooShortName_isRejected() {
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.setLastName("B");
        assertThat(fieldsWithErrors(r)).containsExactly("lastName");
    }
}