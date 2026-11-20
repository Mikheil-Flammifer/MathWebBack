package com.mathweb.repository;

import com.mathweb.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderTransactionId(String providerTransactionId);

    /**
     * @return 1 when inserted, 0 when a payment with this provider
     *         transaction ID already exists
     */
    @Modifying
    @Query(value = """
            INSERT INTO payments (
                user_id,
                plan_id,
                provider,
                provider_transaction_id,
                amount_cents,
                currency,
                status,
                paid_at,
                created_at
            )
            VALUES (
                :userId,
                :planId,
                :provider,
                :transactionId,
                :amountCents,
                :currency,
                'COMPLETED',
                :paidAt,
                NOW()
            )
            ON CONFLICT (provider_transaction_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("planId") Long planId,
            @Param("provider") String provider,
            @Param("transactionId") String transactionId,
            @Param("amountCents") int amountCents,
            @Param("currency") String currency,
            @Param("paidAt") LocalDateTime paidAt
    );
}