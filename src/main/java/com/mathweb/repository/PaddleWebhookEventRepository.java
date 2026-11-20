package com.mathweb.repository;

import com.mathweb.entity.PaddleWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

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
}