package com.mathweb.service.impl;

import com.mathweb.entity.Payment;
import com.mathweb.entity.Plan;
import com.mathweb.entity.Subscription;
import com.mathweb.entity.User;
import com.mathweb.enums.PaymentStatus;
import com.mathweb.enums.SubscriptionStatus;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.PaymentRepository;
import com.mathweb.repository.PlanRepository;
import com.mathweb.repository.SubscriptionRepository;
import com.mathweb.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.temporal.ChronoUnit;

/** Unit tests for {@link AccessGrantService}. */
@ExtendWith(MockitoExtension.class)
class AccessGrantServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PlanRepository planRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SubscriptionRepository subscriptionRepository;

    @InjectMocks private AccessGrantService service;

    private User user;
    private Plan plan;

    @BeforeEach
    void setUp() {
        user = User.builder().email("student@example.com").build();
        user.setId(1L);
        plan = Plan.builder().code("MONTHLY").name("Monthly").durationDays(30).priceCents(999).build();
        plan.setId(2L);
    }

    private Subscription activeSub(LocalDateTime end) {
        return Subscription.builder().user(user).status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(end).amountCents(999).build();
    }

    @Nested
    class GrantAccess {

        @Test
        void returnsFalse_andDoesNothing_whenTransactionAlreadyProcessed() {
            when(paymentRepository.existsByProviderTransactionId("txn_1")).thenReturn(true);

            boolean granted = service.grantAccess(1L, 2L, "paddle", "txn_1", 999, "usd");

            assertThat(granted).isFalse();
            verify(paymentRepository, never()).saveAndFlush(any());
            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        void throwsNotFound_whenUserMissing() {
            when(paymentRepository.existsByProviderTransactionId("txn_1")).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.grantAccess(1L, 2L, "paddle", "txn_1", 999, "usd"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void throwsNotFound_whenPlanMissing() {
            when(paymentRepository.existsByProviderTransactionId("txn_1")).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(planRepository.findById(2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.grantAccess(1L, 2L, "paddle", "txn_1", 999, "usd"))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(paymentRepository, never()).saveAndFlush(any());
        }

        @Test
        void savesCompletedPayment_withUppercasedCurrency_andCreatesSubscription() {
            when(paymentRepository.existsByProviderTransactionId("txn_1")).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(planRepository.findById(2L)).thenReturn(Optional.of(plan));
            when(subscriptionRepository.findByUser(user)).thenReturn(Optional.empty());

            boolean granted = service.grantAccess(1L, 2L, "paddle", "txn_1", 999, "usd");

            assertThat(granted).isTrue();

            ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).saveAndFlush(payment.capture());
            assertThat(payment.getValue().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(payment.getValue().getCurrency()).isEqualTo("USD");
            assertThat(payment.getValue().getProviderTransactionId()).isEqualTo("txn_1");
            assertThat(payment.getValue().getAmountCents()).isEqualTo(999);

            ArgumentCaptor<Subscription> sub = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(sub.capture());
            assertThat(sub.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(sub.getValue().getCurrency()).isEqualTo("USD");
            assertThat(sub.getValue().getAmountCents()).isEqualTo(999);
            assertThat(sub.getValue().getCurrentPeriodEnd())
                    .isCloseTo(LocalDateTime.now().plusDays(30), within(5, ChronoUnit.SECONDS));
        }

        @Test
        void extendsFromCurrentEnd_whenSubscriptionStillActive() {
            LocalDateTime end = LocalDateTime.now().plusDays(10);
            Subscription existing = activeSub(end);
            when(paymentRepository.existsByProviderTransactionId("txn_2")).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(planRepository.findById(2L)).thenReturn(Optional.of(plan));
            when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(existing));

            service.grantAccess(1L, 2L, "paddle", "txn_2", 999, "eur");

            assertThat(existing.getCurrentPeriodEnd()).isEqualTo(end.plusDays(30));
        }

        @Test
        void restartsFromNow_whenSubscriptionExpired() {
            Subscription expired = activeSub(LocalDateTime.now().minusDays(3));
            when(paymentRepository.existsByProviderTransactionId("txn_3")).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(planRepository.findById(2L)).thenReturn(Optional.of(plan));
            when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(expired));

            service.grantAccess(1L, 2L, "paddle", "txn_3", 999, "usd");

            assertThat(expired.getCurrentPeriodStart())
                    .isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
            assertThat(expired.getCurrentPeriodEnd())
                    .isCloseTo(LocalDateTime.now().plusDays(30), within(5, ChronoUnit.SECONDS));
        }
    }

    @Nested
    class GrantFreeDays {

        @Test
        void rejectsNonPositiveDays() {
            assertThatThrownBy(() -> service.grantFreeDays(1L, 0))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Days must be at least 1");
            assertThatThrownBy(() -> service.grantFreeDays(1L, -5))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void throwsNotFound_whenUserMissing() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.grantFreeDays(1L, 7))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void createsSubscription_withZeroAmount_forNewUser() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(subscriptionRepository.findByUser(user)).thenReturn(Optional.empty());
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

            Subscription result = service.grantFreeDays(1L, 7);

            assertThat(result.getAmountCents()).isZero();
            assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(result.getCurrentPeriodEnd())
                    .isCloseTo(LocalDateTime.now().plusDays(7), within(5, ChronoUnit.SECONDS));
        }

        @Test
        void keepsRecordedAmount_andExtendsActiveSubscription() {
            LocalDateTime end = LocalDateTime.now().plusDays(5);
            Subscription existing = activeSub(end);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(existing));
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

            Subscription result = service.grantFreeDays(1L, 10);

            assertThat(result.getAmountCents()).isEqualTo(999);
            assertThat(result.getCurrentPeriodEnd()).isEqualTo(end.plusDays(10));
        }
    }

    @Nested
    class Revoke {

        @Test
        void throwsNotFound_whenUserMissing() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.revoke(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void throwsBadRequest_whenNoSubscription() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(subscriptionRepository.findByUser(user)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.revoke(1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("This user has no active access");
        }

        @Test
        void throwsBadRequest_whenSubscriptionNotActive() {
            Subscription expired = activeSub(LocalDateTime.now().minusDays(1));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> service.revoke(1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("This user has no active access");
            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        void cancelsActiveSubscription_andEndsItNow() {
            Subscription active = activeSub(LocalDateTime.now().plusDays(20));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(active));
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

            Subscription result = service.revoke(1L);

            assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
            assertThat(result.getCancelledAt()).isNotNull();
            assertThat(result.getCurrentPeriodEnd())
                    .isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
            assertThat(result.isCurrentlyActive()).isFalse();
        }
    }
}