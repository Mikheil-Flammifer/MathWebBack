package com.mathweb.entity;

import com.mathweb.enums.PaddleCheckoutOrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "paddle_checkout_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaddleCheckoutOrder {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "expected_price_id", nullable = false, length = 100)
    private String expectedPriceId;

    @Column(name = "expected_subtotal_cents", nullable = false)
    private Long expectedSubtotalCents;

    @Column(name = "expected_currency", nullable = false, length = 3)
    private String expectedCurrency;

    @Column(name = "paddle_transaction_id", unique = true, length = 100)
    private String paddleTransactionId;

    @Column(name = "checkout_url", length = 2048)
    private String checkoutUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaddleCheckoutOrderStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}