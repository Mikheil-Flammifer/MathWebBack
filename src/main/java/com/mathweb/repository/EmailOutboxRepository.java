package com.mathweb.repository;

import com.mathweb.entity.EmailOutbox;
import com.mathweb.enums.EmailDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    long countByStatus(EmailDeliveryStatus status);

    @Modifying
    @Query(value = """
            UPDATE email_outbox
               SET status = 'PROCESSING',
                   processing_owner = :owner,
                   processing_started_at = NOW(),
                   processing_lease_until = NOW() + (:leaseSeconds * INTERVAL '1 second'),
                   attempt_count = attempt_count + 1,
                   updated_at = NOW()
             WHERE id IN (
                   SELECT id FROM email_outbox
                    WHERE (status IN ('PENDING', 'RETRY') AND next_attempt_at <= NOW())
                       OR (status = 'PROCESSING' AND processing_lease_until < NOW())
                    ORDER BY created_at
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
             )
            RETURNING id
            """, nativeQuery = true)
    List<Long> claimBatch(
            @Param("owner") String owner,
            @Param("leaseSeconds") int leaseSeconds,
            @Param("batchSize") int batchSize
    );
}