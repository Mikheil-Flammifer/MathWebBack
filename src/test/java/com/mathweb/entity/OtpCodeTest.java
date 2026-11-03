package com.mathweb.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test of the OtpCode and User helper methods. No Spring, no database.
 */
class OtpCodeTest {

    private static OtpCode otp(LocalDateTime expiresAt, boolean used) {
        return OtpCode.builder()
                .code("123456")
                .expiresAt(expiresAt)
                .used(used)
                .build();
    }

    @Test
    void defaults_areUnusedWithZeroAttemptsAndEmailVerificationPurpose() {
        OtpCode code = OtpCode.builder()
                .code("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        assertThat(code.getUsed()).isFalse();
        assertThat(code.getAttempts()).isZero();
        assertThat(code.getPurpose()).isEqualTo("EMAIL_VERIFICATION");
    }

    @Test
    void futureExpiry_isNotExpired_andValid() {
        OtpCode code = otp(LocalDateTime.now().plusMinutes(5), false);

        assertThat(code.isExpired()).isFalse();
        assertThat(code.isValid()).isTrue();
    }

    @Test
    void pastExpiry_isExpired_andNotValid() {
        OtpCode code = otp(LocalDateTime.now().minusSeconds(1), false);

        assertThat(code.isExpired()).isTrue();
        assertThat(code.isValid()).isFalse();
    }

    @Test
    void usedCode_isNotValid_evenIfNotExpired() {
        OtpCode code = otp(LocalDateTime.now().plusMinutes(5), true);

        assertThat(code.isExpired()).isFalse();
        assertThat(code.isValid()).isFalse();
    }

    @Test
    void user_fullName_joinsFirstAndLastName() {
        User user = User.builder().firstName("Nino").lastName("Beridze").build();

        assertThat(user.getFullName()).isEqualTo("Nino Beridze");
    }

    @Test
    void user_defaults_areUnverifiedActiveAndNoSubscription() {
        User user = User.builder().build();

        assertThat(user.getEmailVerified()).isFalse();
        assertThat(user.getActive()).isTrue();
        assertThat(user.hasActiveSubscription()).isFalse();
    }
}