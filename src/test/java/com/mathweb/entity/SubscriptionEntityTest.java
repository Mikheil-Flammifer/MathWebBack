package com.mathweb.entity;

import com.mathweb.enums.SubscriptionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionEntityTest {

    @Test
    void testIsCurrentlyActive_ActiveAndFutureEnd() {
        Subscription subscription = Subscription.builder()
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(LocalDateTime.now().plusDays(10))
                .build();

        assertTrue(subscription.isCurrentlyActive());
    }

    @Test
    void testIsCurrentlyActive_ActiveAndPastEnd() {
        Subscription subscription = Subscription.builder()
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(LocalDateTime.now().minusDays(1))
                .build();

        assertFalse(subscription.isCurrentlyActive());
    }

    @Test
    void testIsCurrentlyActive_InactiveStatus() {
        Subscription subscription = Subscription.builder()
                .status(SubscriptionStatus.CANCELLED)
                .currentPeriodEnd(LocalDateTime.now().plusDays(10))
                .build();

        assertFalse(subscription.isCurrentlyActive());
    }

    @Test
    void testIsCurrentlyActive_NullStatus() {
        Subscription subscription = Subscription.builder()
                .status(null)
                .currentPeriodEnd(LocalDateTime.now().plusDays(10))
                .build();

        assertFalse(subscription.isCurrentlyActive());
    }

    @Test
    void testIsCurrentlyActive_NullCurrentPeriodEnd() {
        Subscription subscription = Subscription.builder()
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(null)
                .build();

        assertFalse(subscription.isCurrentlyActive());
    }
}