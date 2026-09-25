package com.mathweb.entity;

import com.mathweb.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SubscriptionStatus status;

    @Column(name = "stripe_subscription_id", unique = true)
    private String stripeSubscriptionId;

    @Column(name = "stripe_price_id")
    private String stripePriceId;

    @Column(name = "amount_cents")
    private Integer amountCents;

    @Column(name = "currency", length = 10)
    @Builder.Default
    private String currency = "usd";

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "trial_end")
    private LocalDateTime trialEnd;

    // ===== HELPERS =====

    public boolean isCurrentlyActive() {
        return status == SubscriptionStatus.ACTIVE
                && currentPeriodEnd != null
                && LocalDateTime.now().isBefore(currentPeriodEnd);
    }
}