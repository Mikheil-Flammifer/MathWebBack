package com.mathweb.entity;

import com.mathweb.enums.Role;
import com.mathweb.enums.SubscriptionStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link User} entity.
 * Tests field defaults, business logic methods, and relationships.
 */
class UserTest {

    private static User userWithId(Long id) {
        User user = User.builder().build();
        user.setId(id);
        return user;
    }

    // ==================== DEFAULT VALUES ====================

    @Nested
    class DefaultValues {

        @Test
        void newlyCreatedUser_hasDefaultEmailVerifiedFalse() {
            User user = User.builder().build();
            assertThat(user.getEmailVerified()).isFalse();
        }

        @Test
        void newlyCreatedUser_hasDefaultActiveTrue() {
            User user = User.builder().build();
            assertThat(user.getActive()).isTrue();
        }

        @Test
        void newlyCreatedUser_hasNoSubscription() {
            User user = User.builder().build();
            assertThat(user.getSubscription()).isNull();
            assertThat(user.hasActiveSubscription()).isFalse();
        }

        @Test
        void newlyCreatedUser_hasEmptyCollections() {
            User user = User.builder().build();
            assertThat(user.getOtpCodes()).isEmpty();
            assertThat(user.getComments()).isEmpty();
            assertThat(user.getQuestProgresses()).isEmpty();
            assertThat(user.getProblemAttempts()).isEmpty();
            assertThat(user.getUploadedVideos()).isEmpty();
        }
    }

    // ==================== BUSINESS LOGIC ====================

    @Nested
    class FullName {

        @Test
        void getFullName_returnsConcatenatedFirstAndLastName() {
            User user = User.builder()
                    .firstName("John")
                    .lastName("Doe")
                    .build();

            assertThat(user.getFullName()).isEqualTo("John Doe");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t", "\n"})
        void getFullName_handlesEmptyNames(String empty) {
            User userWithEmptyFirst = User.builder()
                    .firstName(empty)
                    .lastName("Doe")
                    .build();

            User userWithEmptyLast = User.builder()
                    .firstName("John")
                    .lastName(empty)
                    .build();

            assertThat(userWithEmptyFirst.getFullName()).isEqualTo(empty + " Doe");
            assertThat(userWithEmptyLast.getFullName()).isEqualTo("John " + empty);
        }

        @Test
        void getFullName_handlesNullNames() {
            User user = User.builder().build();
            assertThat(user.getFullName()).isEqualTo("null null");
        }
    }

    @Nested
    class ActiveSubscription {

        @Test
        void hasActiveSubscription_returnsFalse_whenNoSubscription() {
            User user = User.builder().build();
            assertThat(user.hasActiveSubscription()).isFalse();
        }

        @Test
        void hasActiveSubscription_returnsFalse_whenSubscriptionIsNull() {
            User user = User.builder().subscription(null).build();
            assertThat(user.hasActiveSubscription()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = SubscriptionStatus.class, names = {"EXPIRED", "INCOMPLETE", "CANCELLED"})
        void hasActiveSubscription_returnsFalse_forNonActiveStatuses(SubscriptionStatus status) {
            Subscription subscription = Subscription.builder()
                    .status(status)
                    .build();

            User user = User.builder()
                    .subscription(subscription)
                    .build();

            assertThat(user.hasActiveSubscription()).isFalse();
        }

        @Test
        void hasActiveSubscription_returnsTrue_whenSubscriptionIsActiveAndNotExpired() {
            Subscription subscription = Subscription.builder()
                    .status(SubscriptionStatus.ACTIVE)
                    .currentPeriodEnd(LocalDateTime.now().plusDays(1))
                    .build();

            User user = User.builder()
                    .subscription(subscription)
                    .build();

            assertThat(user.hasActiveSubscription()).isTrue();
        }

        @Test
        void hasActiveSubscription_returnsFalse_whenSubscriptionIsActiveButExpired() {
            Subscription subscription = Subscription.builder()
                    .status(SubscriptionStatus.ACTIVE)
                    .currentPeriodEnd(LocalDateTime.now().minusDays(1))
                    .build();

            User user = User.builder()
                    .subscription(subscription)
                    .build();

            assertThat(user.hasActiveSubscription()).isFalse();
        }
    }

    // ==================== RELATIONSHIPS ====================

    @Nested
    class Relationships {

        @Test
        void user_canHaveMultipleOtpCodes() {
            User user = User.builder()
                    .otpCodes(new ArrayList<>())
                    .build();

            OtpCode otp1 = OtpCode.builder()
                    .code("123456")
                    .user(user)
                    .build();

            OtpCode otp2 = OtpCode.builder()
                    .code("654321")
                    .user(user)
                    .build();

            user.getOtpCodes().add(otp1);
            user.getOtpCodes().add(otp2);

            assertThat(user.getOtpCodes()).hasSize(2);
            assertThat(user.getOtpCodes()).containsExactly(otp1, otp2);
        }

        @Test
        void user_canHaveSubscription() {
            Subscription subscription = Subscription.builder()
                    .status(SubscriptionStatus.ACTIVE)
                    .build();

            User user = User.builder()
                    .subscription(subscription)
                    .build();

            assertThat(user.getSubscription()).isEqualTo(subscription);
        }

        @Test
        void user_canHaveComments() {
            User user = User.builder()
                    .comments(new ArrayList<>())
                    .build();

            Comment comment = Comment.builder()
                    .content("Test comment")
                    .user(user)
                    .build();

            user.getComments().add(comment);

            assertThat(user.getComments()).hasSize(1);
            assertThat(user.getComments()).contains(comment);
        }
    }

    // ==================== BUILDER ====================

    @Nested
    class Builder {

        @Test
        void builder_setsAllFieldsCorrectly() {
            Subscription subscription = Subscription.builder().build();
            List<OtpCode> otpCodes = new ArrayList<>();
            List<Comment> comments = new ArrayList<>();

            User user = User.builder()
                    .firstName("John")
                    .lastName("Doe")
                    .email("john@example.com")
                    .passwordHash("hashed_password")
                    .role(Role.STUDENT)
                    .emailVerified(true)
                    .active(true)
                    .avatarUrl("http://example.com/avatar.jpg")
                    .subscription(subscription)
                    .otpCodes(otpCodes)
                    .comments(comments)
                    .questProgresses(new ArrayList<>())
                    .problemAttempts(new ArrayList<>())
                    .uploadedVideos(new ArrayList<>())
                    .build();

            assertThat(user.getFirstName()).isEqualTo("John");
            assertThat(user.getLastName()).isEqualTo("Doe");
            assertThat(user.getEmail()).isEqualTo("john@example.com");
            assertThat(user.getPasswordHash()).isEqualTo("hashed_password");
            assertThat(user.getRole()).isEqualTo(Role.STUDENT);
            assertThat(user.getEmailVerified()).isTrue();
            assertThat(user.getActive()).isTrue();
            assertThat(user.getAvatarUrl()).isEqualTo("http://example.com/avatar.jpg");
            assertThat(user.getSubscription()).isEqualTo(subscription);
            assertThat(user.getOtpCodes()).isSameAs(otpCodes);
            assertThat(user.getComments()).isSameAs(comments);
        }

        @Test
        void builder_withMinimalFields_createsValidUser() {
            User user = User.builder()
                    .email("test@example.com")
                    .passwordHash("password")
                    .role(Role.STUDENT)
                    .build();

            assertThat(user.getEmail()).isEqualTo("test@example.com");
            assertThat(user.getPasswordHash()).isEqualTo("password");
            assertThat(user.getRole()).isEqualTo(Role.STUDENT);
            assertThat(user.getEmailVerified()).isFalse();
            assertThat(user.getActive()).isTrue();
        }
    }

    // ==================== EQUALITY ====================

    @Nested
    class Equality {

        @Test
        void equals_returnsTrue_forSameObject() {
            User user = userWithId(1L);
            assertThat(user).isEqualTo(user);
        }

        @Test
        void equals_returnsFalse_forNull() {
            User user = userWithId(1L);
            assertThat(user).isNotEqualTo(null);
        }

        @Test
        void equals_returnsFalse_forDifferentClass() {
            User user = userWithId(1L);
            assertThat(user).isNotEqualTo("not a user");
        }

        @Test
        void equals_returnsTrue_forSameId() {
            assertThat(userWithId(1L)).isEqualTo(userWithId(1L));
        }

        @Test
        void equals_returnsFalse_forDifferentId() {
            assertThat(userWithId(1L)).isNotEqualTo(userWithId(2L));
        }
    }
}