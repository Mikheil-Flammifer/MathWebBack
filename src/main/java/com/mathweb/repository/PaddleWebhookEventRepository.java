package com.mathweb.repository;

import com.mathweb.entity.PaddleWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PaddleWebhookEventRepository
        extends JpaRepository<PaddleWebhookEvent, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO paddle_webhook_events (
                event_id,
                notification_id,
                event_type,
                occurred_at,
                source,
                payload,
                status,
                attempt_count,
                next_attempt_at,
                created_at
            )
            VALUES (
                :eventId,
                :notificationId,
                :eventType,
                :occurredAt,
                'WEBHOOK',
                CAST(:payload AS jsonb),
                'RECEIVED',
                0,
                NOW(),
                NOW()
            )
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") String eventId,
            @Param("notificationId") String notificationId,
            @Param("eventType") String eventType,
            @Param("occurredAt") Instant occurredAt,
            @Param("payload") String payload
    );

    @Query(value = """
        UPDATE paddle_webhook_events
           SET status = 'PROCESSING',
               processing_owner = :owner,
               processing_started_at = NOW(),
               processing_lease_until = NOW() + (:leaseSeconds * INTERVAL '1 second'),
               attempt_count = attempt_count + 1,
               updated_at = NOW()
         WHERE id IN (
               SELECT id FROM paddle_webhook_events
                WHERE (status IN ('RECEIVED', 'RETRY') AND next_attempt_at <= NOW())
                   OR (status = 'PROCESSING' AND processing_lease_until < NOW())
                ORDER BY occurred_at
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
         )
        RETURNING id
        """, nativeQuery = true)
    List<Long> claimBatch(@Param("owner") String owner,
                          @Param("leaseSeconds") int leaseSeconds,
                          @Param("batchSize") int batchSize);

    @Modifying
    @Query(value = """
        INSERT INTO paddle_webhook_events (
            event_id, notification_id, event_type, occurred_at,
            source, payload, status, attempt_count, next_attempt_at, created_at
        )
        VALUES (
            :eventId, NULL, :eventType, :occurredAt,
            'RECONCILIATION', CAST(:payload AS jsonb), 'RECEIVED', 0, NOW(), NOW()
        )
        ON CONFLICT (event_id) DO NOTHING
        """, nativeQuery = true)
    int insertReconciledIfAbsent(
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("occurredAt") Instant occurredAt,
            @Param("payload") String payload
    );
}