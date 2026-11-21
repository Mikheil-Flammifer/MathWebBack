package com.mathweb.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathweb.entity.Plan;
import com.mathweb.entity.Subscription;
import com.mathweb.entity.User;
import com.mathweb.enums.Role;
import com.mathweb.repository.PlanRepository;
import com.mathweb.repository.SubscriptionRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.service.impl.PaddleApiClient;
import com.mathweb.service.impl.PaddleReconciliationService;
import com.mathweb.service.impl.PaddleWebhookProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

class PaddleReconciliationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PRICE_ID = "pri_test_pass30";

    @Autowired PaddleReconciliationService reconciliation;
    @Autowired PaddleWebhookProcessor processor;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @MockBean PaddleApiClient paddleApiClient;

    private Plan pass30;

    @BeforeEach
    void setUp() {
        jdbc.update("UPDATE paddle_webhook_events SET status = 'IGNORED' "
                + "WHERE status IN ('RECEIVED', 'RETRY', 'PROCESSING')");
        jdbc.update("UPDATE plans SET paddle_price_id = ? WHERE code = 'PASS_30'", PRICE_ID);
        pass30 = planRepository.findByCodeAndActiveTrue("PASS_30").orElseThrow();
    }

    // 12. Reconciliation recovers an event that was never delivered
    @Test
    void undeliveredEvent_isRecovered_processed_andGrantsAccess() throws Exception {
        User user = createUser();
        String txn = newTxn();
        createOrder(user, txn);
        String eventId = newEventId();

        when(paddleApiClient.listEvents(isNull(), anyInt()))
                .thenReturn(page(false, completedEvent(eventId, txn, Instant.now())));

        assertThat(reconciliation.reconcile()).isEqualTo(1);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT source, status, notification_id FROM paddle_webhook_events "
                        + "WHERE event_id = ?", eventId);
        assertThat(row.get("source")).isEqualTo("RECONCILIATION");
        assertThat(row.get("status")).isEqualTo("RECEIVED");
        assertThat(row.get("notification_id")).isNull();

        processor.poll();

        assertThat(eventStatus(eventId)).isEqualTo("PROCESSED");
        assertThat(paymentCount(txn)).isEqualTo(1);
        Subscription sub = subscriptionRepository.findByUser(user).orElseThrow();
        assertThat(sub.getCurrentPeriodEnd()).isAfter(java.time.LocalDateTime.now().plusDays(29));
    }

    @Test
    void eventAlreadyInInbox_isNotDuplicated_andKeepsItsSource() throws Exception {
        String txn = newTxn();
        String eventId = newEventId();
        insertWebhookEvent(eventId, txn);

        when(paddleApiClient.listEvents(isNull(), anyInt()))
                .thenReturn(page(false, completedEvent(eventId, txn, Instant.now())));

        assertThat(reconciliation.reconcile()).isZero();

        assertThat(rowCount(eventId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT source FROM paddle_webhook_events WHERE event_id = ?",
                String.class, eventId)).isEqualTo("WEBHOOK");
    }

    @Test
    void secondRun_isNoOp() throws Exception {
        String eventId = newEventId();
        when(paddleApiClient.listEvents(isNull(), anyInt()))
                .thenReturn(page(false, completedEvent(eventId, newTxn(), Instant.now())));

        assertThat(reconciliation.reconcile()).isEqualTo(1);
        assertThat(reconciliation.reconcile()).isZero();
        assertThat(rowCount(eventId)).isEqualTo(1);
    }

    @Test
    void onlyTransactionCompletedEvents_areRecovered() throws Exception {
        String completed = newEventId();
        String other = newEventId();
        ObjectNode adjustment = completedEvent(other, newTxn(), Instant.now());
        adjustment.put("event_type", "adjustment.created");

        when(paddleApiClient.listEvents(isNull(), anyInt()))
                .thenReturn(page(false, completedEvent(completed, newTxn(), Instant.now()), adjustment));

        assertThat(reconciliation.reconcile()).isEqualTo(1);
        assertThat(rowCount(completed)).isEqualTo(1);
        assertThat(rowCount(other)).isZero();
    }

    @Test
    void eventsOlderThanLookback_areNotRecovered_andPagingStops() throws Exception {
        String recent = newEventId();
        String old = newEventId();

        when(paddleApiClient.listEvents(isNull(), anyInt()))
                .thenReturn(page(true,
                        completedEvent(recent, newTxn(), Instant.now()),
                        completedEvent(old, newTxn(), Instant.now().minus(30, ChronoUnit.DAYS))));

        assertThat(reconciliation.reconcile()).isEqualTo(1);
        assertThat(rowCount(recent)).isEqualTo(1);
        assertThat(rowCount(old)).isZero();
    }

    @Test
    void multiplePages_areFollowedUsingTheLastEventId() throws Exception {
        String first = newEventId();
        String second = newEventId();

        when(paddleApiClient.listEvents(isNull(), anyInt()))
                .thenReturn(page(true, completedEvent(first, newTxn(), Instant.now())));
        when(paddleApiClient.listEvents(org.mockito.ArgumentMatchers.eq(first), anyInt()))
                .thenReturn(page(false, completedEvent(second, newTxn(), Instant.now())));

        assertThat(reconciliation.reconcile()).isEqualTo(2);
        assertThat(rowCount(first)).isEqualTo(1);
        assertThat(rowCount(second)).isEqualTo(1);
    }

    // ---------------------------------------------------------------
    private com.fasterxml.jackson.databind.JsonNode page(boolean hasMore, ObjectNode... events) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode data = root.putArray("data");
        for (ObjectNode e : events) {
            data.add(e);
        }
        root.putObject("meta").putObject("pagination").put("has_more", hasMore);
        return root;
    }

    private ObjectNode completedEvent(String eventId, String txn, Instant occurredAt) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("event_id", eventId);
        event.put("event_type", "transaction.completed");
        event.put("occurred_at", occurredAt.toString());

        ObjectNode data = event.putObject("data");
        data.put("id", txn);
        data.put("status", "completed");
        data.put("currency_code", "USD");
        data.putNull("discount_id");
        data.putArray("items").addObject()
                .put("quantity", 1)
                .putObject("price").put("id", PRICE_ID);
        ObjectNode details = data.putObject("details");
        ObjectNode totals = details.putObject("totals");
        totals.put("discount", "0");
        totals.put("grand_total", "199");
        ObjectNode line = details.putArray("line_items").addObject();
        line.put("price_id", PRICE_ID);
        line.put("quantity", 1);
        line.putObject("totals").put("subtotal", "199").put("discount", "0");
        return event;
    }

    private void insertWebhookEvent(String eventId, String txn) throws Exception {
        ObjectNode event = completedEvent(eventId, txn, Instant.now());
        jdbc.update("""
                INSERT INTO paddle_webhook_events
                    (event_id, notification_id, event_type, occurred_at, source,
                     payload, status, attempt_count, next_attempt_at, created_at)
                VALUES (?, 'ntf_x', 'transaction.completed', ?, 'WEBHOOK',
                        CAST(? AS jsonb), 'PROCESSED', 1, NOW(), NOW())
                """, eventId, Timestamp.from(Instant.now()), objectMapper.writeValueAsString(event));
    }

    private void createOrder(User user, String txn) {
        jdbc.update("""
                INSERT INTO paddle_checkout_orders
                    (id, user_id, plan_id, expected_price_id, expected_subtotal_cents,
                     expected_currency, paddle_transaction_id, status, expires_at)
                VALUES (?, ?, ?, ?, ?, 'USD', ?, 'READY', ?)
                """, UUID.randomUUID(), user.getId(), pass30.getId(), PRICE_ID,
                (long) pass30.getPriceCents(), txn,
                Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)));
    }

    private int rowCount(String eventId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM paddle_webhook_events WHERE event_id = ?",
                Integer.class, eventId);
    }

    private String eventStatus(String eventId) {
        return jdbc.queryForObject(
                "SELECT status FROM paddle_webhook_events WHERE event_id = ?",
                String.class, eventId);
    }

    private int paymentCount(String txn) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE provider_transaction_id = ?",
                Integer.class, txn);
    }

    private static String newTxn() {
        return "txn_" + UUID.randomUUID();
    }

    private static String newEventId() {
        return "evt_" + UUID.randomUUID().toString().replace("-", "");
    }

    private User createUser() {
        return userRepository.saveAndFlush(
                User.builder()
                        .email("it-" + UUID.randomUUID() + "@example.test")
                        .firstName("Test")
                        .lastName("User")
                        .passwordHash("not-used")
                        .role(Role.STUDENT)
                        .build());
    }
}