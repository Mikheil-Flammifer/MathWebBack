package com.mathweb.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Recovers transaction.completed events that Paddle never delivered by webhook.
 * Events go into the same inbox (source RECONCILIATION) and are processed by
 * PaddleWebhookProcessor, so validation and idempotency are identical.
 */
@Component
public class PaddleReconciliationService {

    private static final Logger log =
            LoggerFactory.getLogger(PaddleReconciliationService.class);

    private static final int PER_PAGE = 200;
    private static final int MAX_PAGES = 50;

    private final PaddleApiClient apiClient;
    private final PaddleWebhookInboxService inboxService;
    private final ObjectMapper objectMapper;
    private final Duration lookback;

    public PaddleReconciliationService(
            PaddleApiClient apiClient,
            PaddleWebhookInboxService inboxService,
            ObjectMapper objectMapper,
            @Value("${paddle.reconciliation.lookback:PT48H}") Duration lookback
    ) {
        if (lookback.isNegative() || lookback.isZero()) {
            throw new IllegalStateException("Paddle reconciliation lookback must be positive");
        }
        this.apiClient = apiClient;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
        this.lookback = lookback;
    }

    @Scheduled(
            initialDelayString = "${paddle.reconciliation.initial-delay-ms:60000}",
            fixedDelayString = "${paddle.reconciliation.poll-ms:900000}")
    public void scheduledRun() {
        try {
            int inserted = reconcile();
            if (inserted > 0) {
                log.warn("Paddle reconciliation recovered {} undelivered event(s)", inserted);
            }
        } catch (RuntimeException exception) {
            // Next run retries; every insert is independent and idempotent.
            log.error("Paddle reconciliation failed", exception);
        }
    }

    /** @return number of events newly added to the inbox */
    public int reconcile() {
        Instant cutoff = Instant.now().minus(lookback);
        int inserted = 0;
        String after = null;

        for (int page = 0; page < MAX_PAGES; page++) {
            JsonNode body = apiClient.listEvents(after, PER_PAGE);
            JsonNode events = body.path("data");
            if (events.isEmpty()) {
                break;
            }

            String lastId = null;
            boolean reachedCutoff = false;

            for (JsonNode event : events) {
                String eventId = text(event, "event_id");
                String eventType = text(event, "event_type");
                String occurredAtText = text(event, "occurred_at");
                if (eventId == null || eventType == null || occurredAtText == null) {
                    log.warn("Skipping malformed Paddle event in reconciliation");
                    continue;
                }
                lastId = eventId;

                Instant occurredAt = OffsetDateTime.parse(occurredAtText).toInstant();
                if (occurredAt.isBefore(cutoff)) {
                    reachedCutoff = true; // newest-first, so everything after is older
                    break;
                }
                if (!"transaction.completed".equals(eventType)) {
                    continue;
                }
                if (inboxService.storeReconciled(
                        eventId, eventType, occurredAt, event.toString())) {
                    inserted++;
                }
            }

            if (reachedCutoff || lastId == null
                    || !body.path("meta").path("pagination").path("has_more").asBoolean(false)) {
                break;
            }
            after = lastId;
        }
        return inserted;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
    }
}