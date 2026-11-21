package com.mathweb.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathweb.controller.PaddleWebhookController;
import com.mathweb.security.PaddleSignatureVerifier;
import com.mathweb.service.impl.PaddleWebhookInboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

// The poller is slowed down so it cannot claim rows while we assert on them.
@SpringBootTest(properties = "paddle.processor.poll-ms=3600000")
class PaddleWebhookInboxIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String SIGNATURE = "ts=1700000000;h1=abc";

    @Autowired PaddleWebhookController controller;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    // Signature maths is covered by PaddleSignatureVerifierTest; here we test persistence.
    @MockBean PaddleSignatureVerifier signatureVerifier;

    // Real behaviour by default, failure injected only where a test asks for it.
    @SpyBean PaddleWebhookInboxService inboxService;

    @BeforeEach
    void signatureIsValidByDefault() {
        when(signatureVerifier.verify(nullable(String.class), any(byte[].class)))
                .thenReturn(true);
    }

    // ---------------------------------------------------------------
    // 6. 200 only after persistence
    // ---------------------------------------------------------------
    @Test
    void validEvent_returns200_andRowIsAlreadyCommitted() throws Exception {
        String eventId = newEventId();
        String raw = rawEvent(eventId, "transaction.completed");

        ResponseEntity<Void> response = post(raw);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        // Read on a separate connection: the row must be committed when we get 200.
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT event_type, notification_id, source, status, attempt_count, "
                        + "payload::text AS payload FROM paddle_webhook_events WHERE event_id = ?",
                eventId);

        assertThat(row.get("event_type")).isEqualTo("transaction.completed");
        assertThat(row.get("notification_id")).isEqualTo("ntf_01j123456789abcdefghijklm");
        assertThat(row.get("source")).isEqualTo("WEBHOOK");
        assertThat(row.get("attempt_count")).isEqualTo(0);
        // jsonb normalises whitespace and key order, so compare parsed trees
        assertThat(objectMapper.readTree((String) row.get("payload")))
                .isEqualTo(objectMapper.readTree(raw));
    }

    @Test
    void invalidSignature_returns401_andStoresNothing() throws Exception {
        when(signatureVerifier.verify(nullable(String.class), any(byte[].class)))
                .thenReturn(false);
        String eventId = newEventId();

        ResponseEntity<Void> response = post(rawEvent(eventId, "transaction.completed"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(rowCount(eventId)).isZero();
    }

    @Test
    void malformedEnvelope_returns400_andStoresNothing() throws Exception {
        String eventId = newEventId();
        ObjectNode event = (ObjectNode) objectMapper.readTree(
                rawEvent(eventId, "transaction.completed"));
        event.remove("occurred_at");

        ResponseEntity<Void> response = post(objectMapper.writeValueAsString(event));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(rowCount(eventId)).isZero();
    }

    // ---------------------------------------------------------------
    // 7. Persistence failure is not swallowed (becomes 5xx)
    // ---------------------------------------------------------------
    @Test
    void persistenceFailure_propagates_storesNothing_andRedeliverySucceeds()
            throws Exception {

        String eventId = newEventId();
        String raw = rawEvent(eventId, "transaction.completed");

        // First call fails, second call runs the real method.
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .doCallRealMethod()
                .when(inboxService)
                .store(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> post(raw))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(rowCount(eventId)).isZero();

        // Paddle retries; the event must be accepted now.
        assertThat(post(raw).getStatusCode().value()).isEqualTo(200);
        assertThat(rowCount(eventId)).isEqualTo(1);
    }

    // ---------------------------------------------------------------
    // 8. Duplicate event ID creates one inbox row
    // ---------------------------------------------------------------
    @Test
    void sameEventDeliveredTwice_bothAcknowledged_oneRow() throws Exception {
        String eventId = newEventId();
        String raw = rawEvent(eventId, "transaction.completed");

        assertThat(post(raw).getStatusCode().value()).isEqualTo(200);
        assertThat(post(raw).getStatusCode().value()).isEqualTo(200);

        assertThat(rowCount(eventId)).isEqualTo(1);
    }

    @Test
    void sameEventDeliveredConcurrently_oneRow_noErrors() throws Exception {
        String eventId = newEventId();
        String raw = rawEvent(eventId, "transaction.completed");

        int threads = 5;
        CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return post(raw).getStatusCode().value();
                }));
            }
            for (Future<Integer> f : futures) {
                assertThat(f.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(rowCount(eventId)).isEqualTo(1);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private ResponseEntity<Void> post(String raw) {
        return controller.handle(SIGNATURE, raw.getBytes(StandardCharsets.UTF_8));
    }

    private int rowCount(String eventId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM paddle_webhook_events WHERE event_id = ?",
                Integer.class, eventId);
    }

    private static String newEventId() {
        return "evt_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String rawEvent(String eventId, String eventType) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("event_id", eventId);
        root.put("notification_id", "ntf_01j123456789abcdefghijklm");
        root.put("event_type", eventType);
        root.put("occurred_at", "2026-11-20T10:15:30.123Z");
        root.putObject("data").put("id", "txn_" + UUID.randomUUID());
        return objectMapper.writeValueAsString(root);
    }
}