package com.mathweb.service.impl;

import com.mathweb.entity.RefreshToken;
import com.mathweb.entity.Subscription;
import com.mathweb.entity.User;
import com.mathweb.enums.NotificationType;
import com.mathweb.enums.SubscriptionStatus;
import com.mathweb.repository.OtpCodeRepository;
import com.mathweb.repository.RefreshTokenRepository;
import com.mathweb.repository.SubscriptionRepository;
import com.mathweb.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link ScheduledTaskService}. */
@ExtendWith(MockitoExtension.class)
class ScheduledTaskServiceTest {

    @Mock private OtpCodeRepository otpCodeRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private RateLimitService rateLimitService;
    @Mock private NotificationService notificationService;

    @InjectMocks private ScheduledTaskService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().email("student@example.com").build();
        user.setId(1L);
    }

    private Subscription subscription(SubscriptionStatus status, LocalDateTime end) {
        return Subscription.builder().user(user).status(status).currentPeriodEnd(end).build();
    }

    @Test
    void cleanupExpiredOtps_deletesCodesExpiredUpToNow() {
        service.cleanupExpiredOtps();

        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(otpCodeRepository).deleteExpiredCodes(now.capture());
        assertThat(now.getValue()).isBetween(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cleanupExpiredRefreshTokens_deletesOnlyInvalidTokens() {
        RefreshToken valid = RefreshToken.builder().user(user).token("ok")
                .expiresAt(LocalDateTime.now().plusDays(1)).build();
        RefreshToken expired = RefreshToken.builder().user(user).token("old")
                .expiresAt(LocalDateTime.now().minusDays(1)).build();
        RefreshToken revoked = RefreshToken.builder().user(user).token("rev")
                .expiresAt(LocalDateTime.now().plusDays(1)).revoked(true).build();
        when(refreshTokenRepository.findAll()).thenReturn(List.of(valid, expired, revoked));

        service.cleanupExpiredRefreshTokens();

        ArgumentCaptor<List<RefreshToken>> deleted = ArgumentCaptor.forClass(List.class);
        verify(refreshTokenRepository).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).containsExactlyInAnyOrder(expired, revoked);
    }

    @Test
    void checkExpiredSubscriptions_marksExpired_savesAndNotifiesOncePerEndDate() {
        LocalDateTime end = LocalDateTime.now().minusHours(2);
        Subscription sub = subscription(SubscriptionStatus.ACTIVE, end);
        when(subscriptionRepository.findExpiredActiveSubscriptions(any(LocalDateTime.class)))
                .thenReturn(List.of(sub));

        service.checkExpiredSubscriptions();

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        verify(subscriptionRepository).save(sub);
        verify(notificationService).notifyOnce(eq(1L), eq(NotificationType.SUBSCRIPTION_EXPIRED),
                eq("Your access has expired"), anyString(), eq("/subscription"),
                eq("SUB_EXPIRED:" + end.toLocalDate()));
    }

    @Test
    void checkExpiredSubscriptions_doesNothing_whenNoneExpired() {
        when(subscriptionRepository.findExpiredActiveSubscriptions(any(LocalDateTime.class)))
                .thenReturn(List.of());

        service.checkExpiredSubscriptions();

        verify(subscriptionRepository, never()).save(any());
        verify(notificationService, never()).notifyOnce(any(), any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cleanupIncompleteSubscriptions_deletesOnlyThoseOlderThanOneDay() {
        Subscription stale = subscription(SubscriptionStatus.INCOMPLETE, null);
        stale.setCreatedAt(LocalDateTime.now().minusDays(2));
        Subscription fresh = subscription(SubscriptionStatus.INCOMPLETE, null);
        fresh.setCreatedAt(LocalDateTime.now().minusHours(1));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.INCOMPLETE))
                .thenReturn(List.of(stale, fresh));

        service.cleanupIncompleteSubscriptions();

        ArgumentCaptor<List<Subscription>> deleted = ArgumentCaptor.forClass(List.class);
        verify(subscriptionRepository).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(stale);
    }

    @Test
    void cleanupRateLimitEntries_delegatesToRateLimitService() {
        service.cleanupRateLimitEntries();

        verify(rateLimitService).cleanupExpiredEntries();
    }

    @Test
    void cleanupBlacklistedTokens_delegatesToBlacklistService() {
        service.cleanupBlacklistedTokens();

        verify(tokenBlacklistService).removeExpiredTokens();
    }

    @Test
    void notifyExpiringSubscriptions_sendsReminderWithDedupeKeyPerEndDate() {
        LocalDateTime end = LocalDateTime.now().plusDays(2);
        Subscription sub = subscription(SubscriptionStatus.ACTIVE, end);
        when(subscriptionRepository.findActiveEndingBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(sub));

        service.notifyExpiringSubscriptions();

        verify(notificationService).notifyOnce(eq(1L), eq(NotificationType.SUBSCRIPTION_EXPIRING),
                eq("Your access ends soon"), anyString(), eq("/subscription"),
                eq("SUB_EXPIRING:" + end.toLocalDate()));
    }

    @Test
    void notifyExpiringSubscriptions_looksThreeDaysAhead() {
        when(subscriptionRepository.findActiveEndingBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        service.notifyExpiringSubscriptions();

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(subscriptionRepository).findActiveEndingBetween(from.capture(), to.capture());
        assertThat(to.getValue()).isEqualTo(from.getValue().plusDays(3));
    }

    @Test
    void purgeOldNotifications_purgesOlderThanNinetyDays() {
        when(notificationService.purgeOlderThan(90)).thenReturn(4);

        service.purgeOldNotifications();

        verify(notificationService).purgeOlderThan(90);
    }
}