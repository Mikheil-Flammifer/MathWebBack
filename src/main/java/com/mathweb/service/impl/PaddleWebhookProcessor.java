package com.mathweb.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathweb.entity.PaddleCheckoutOrder;
import com.mathweb.entity.PaddleWebhookEvent;
import com.mathweb.enums.PaddleCheckoutOrderStatus;
import com.mathweb.enums.PaddleWebhookEventStatus;
import com.mathweb.exception.PaymentIdentityConflictException;
import com.mathweb.repository.PaddleCheckoutOrderRepository;
import com.mathweb.repository.PaddleWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


/**
 * Processes Paddle webhook events stored in the inbox.
 *
 * ACCESS POLICY: access is sold as a one-time pass with no renewal.
 * Paid access is never revoked automatically. Refunds, credits and
 * chargebacks (adjustment.* events) do NOT end access; it runs until it
 * expires. Such events are kept in the inbox as IGNORED, with a note, for
 * manual bookkeeping. To change this policy, handle adjustment.* here.
 */
@Component
public class PaddleWebhookProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(PaddleWebhookProcessor.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 20;
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final Duration[] BACKOFF = {
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2)
    };

    private final String owner;

    private final PaddleWebhookEventRepository eventRepository;
    private final PaddleCheckoutOrderRepository orderRepository;
    private final PaddleTransactionValidator validator;
    private final AccessGrantService accessGrantService;
    private final TransactionTemplate tx;

    @Autowired
    public PaddleWebhookProcessor(
            PaddleWebhookEventRepository eventRepository,
            PaddleCheckoutOrderRepository orderRepository,
            PaddleTransactionValidator validator,
            AccessGrantService accessGrantService,
            PlatformTransactionManager transactionManager
    ) {
        this(eventRepository, orderRepository, validator, accessGrantService,
                transactionManager, "paddle-processor-" + UUID.randomUUID());
    }

    // Package-private so tests can pick a known owner.
    PaddleWebhookProcessor(
            PaddleWebhookEventRepository eventRepository,
            PaddleCheckoutOrderRepository orderRepository,
            PaddleTransactionValidator validator,
            AccessGrantService accessGrantService,
            PlatformTransactionManager transactionManager,
            String owner
    ) {
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.validator = validator;
        this.accessGrantService = accessGrantService;
        this.tx = new TransactionTemplate(transactionManager);
        this.owner = owner;
    }

    @Scheduled(
            initialDelayString = "${paddle.processor.initial-delay-ms:5000}",
            fixedDelayString = "${paddle.processor.poll-ms:5000}")
    public void poll() {
        List<Long> ids = tx.execute(status ->
                eventRepository.claimBatch(
                        owner, (int) LEASE.toSeconds(), BATCH_SIZE));

        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            processOne(id);
        }
    }

    private void processOne(Long eventId) {
        try {
            tx.executeWithoutResult(status -> handle(eventId));
        } catch (PaymentIdentityConflictException exception) {
            log.error("SECURITY ALERT: payment identity conflict for event row {}",
                    eventId, exception);
            deadLetter(eventId, exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Paddle event row {} failed", eventId, exception);
            recordFailure(eventId, exception);
        }
    }

    /** Runs in one transaction: order completion, payment and access commit together. */
    private void handle(Long eventId) {
        PaddleWebhookEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Event disappeared: " + eventId));

        if (!ownsLease(event)) {
            return;
        }

        String type = event.getEventType();

        if (type.startsWith("adjustment.")) {
            log.warn("Paddle adjustment event {} ({}) recorded; access not revoked by policy",
                    event.getEventId(), type);
            finish(event, PaddleWebhookEventStatus.IGNORED,
                    "Adjustment recorded, access not revoked by policy");
            return;
        }

        if (!"transaction.completed".equals(type)) {
            finish(event, PaddleWebhookEventStatus.IGNORED,
                    "Event type not handled: " + type);
            return;
        }

        JsonNode data = event.getPayload().get("data");
        String txnId = data != null && data.hasNonNull("id")
                ? data.get("id").asText() : null;

        if (txnId == null || txnId.isBlank()) {
            finish(event, PaddleWebhookEventStatus.DEAD_LETTER,
                    "Missing data.id");
            return;
        }

        // Not found may be a race with markReady(), so retry instead of dead-lettering.
        PaddleCheckoutOrder order = orderRepository
                .findByPaddleTransactionIdForUpdate(txnId)
                .orElseThrow(() -> new IllegalStateException(
                        "No checkout order for transaction " + txnId));

        if (order.getStatus() == PaddleCheckoutOrderStatus.COMPLETED) {
            finish(event, PaddleWebhookEventStatus.IGNORED,
                    "Order already completed");
            return;
        }

        PaddleTransactionValidator.Result result =
                validator.validate(data, order, event.getOccurredAt());

        if (!result.valid()) {
            // Money may have been taken, so keep the order untouched for manual review.
            log.error("SECURITY ALERT: Paddle transaction {} rejected: {}",
                    txnId, result.reason());
            finish(event, PaddleWebhookEventStatus.DEAD_LETTER,
                    result.reason());
            return;
        }

        // grantAccess locks the user itself.
        accessGrantService.grantAccess(
                order.getUser().getId(),
                order.getPlan().getId(),
                "PADDLE",
                txnId,
                Math.toIntExact(result.actualTotalCents()),
                order.getExpectedCurrency()
        );

        order.setStatus(PaddleCheckoutOrderStatus.COMPLETED);
        order.setCompletedAt(Instant.now());
        order.setActualSubtotalCents(result.actualSubtotalCents());
        order.setActualTotalCents(result.actualTotalCents());
        order.setFailureReason(null);
        orderRepository.save(order);

        finish(event, PaddleWebhookEventStatus.PROCESSED, null);
    }

    private void deadLetter(Long eventId, String error) {
        try {
            tx.executeWithoutResult(status -> eventRepository.findById(eventId)
                    .filter(this::ownsLease)
                    .ifPresent(e -> finish(e,
                            PaddleWebhookEventStatus.DEAD_LETTER, error)));
        } catch (RuntimeException failure) {
            log.error("Could not dead-letter event {}", eventId, failure);
        }
    }

    private void recordFailure(Long eventId, RuntimeException exception) {
        try {
            tx.executeWithoutResult(status -> {
                PaddleWebhookEvent event =
                        eventRepository.findById(eventId).orElse(null);
                if (event == null || !ownsLease(event)) {
                    return;
                }

                String error = truncate(exception.toString());
                if (event.getAttemptCount() >= MAX_ATTEMPTS) {
                    finish(event, PaddleWebhookEventStatus.DEAD_LETTER, error);
                    return;
                }

                int index = Math.min(event.getAttemptCount() - 1,
                        BACKOFF.length - 1);
                event.setStatus(PaddleWebhookEventStatus.RETRY);
                event.setNextAttemptAt(Instant.now().plus(BACKOFF[index]));
                event.setLastError(error);
                clearLease(event);
                eventRepository.save(event);
            });
        } catch (RuntimeException failure) {
            // The lease will expire and the event will be claimed again.
            log.error("Could not record failure for event {}", eventId, failure);
        }
    }

    private boolean ownsLease(PaddleWebhookEvent event) {
        return event.getStatus() == PaddleWebhookEventStatus.PROCESSING
                && owner.equals(event.getProcessingOwner());
    }

    private void finish(PaddleWebhookEvent event,
                        PaddleWebhookEventStatus status,
                        String note) {
        event.setStatus(status);
        event.setLastError(note == null ? null : truncate(note));
        if (status == PaddleWebhookEventStatus.PROCESSED
                || status == PaddleWebhookEventStatus.IGNORED) {
            event.setProcessedAt(Instant.now());
        }
        clearLease(event);
        eventRepository.save(event);
    }

    private static void clearLease(PaddleWebhookEvent event) {
        event.setProcessingOwner(null);
        event.setProcessingLeaseUntil(null);
        event.setProcessingStartedAt(null);
        event.setUpdatedAt(Instant.now());
    }

    private static String truncate(String s) {
        return s.length() <= 1000 ? s : s.substring(0, 1000);
    }
}