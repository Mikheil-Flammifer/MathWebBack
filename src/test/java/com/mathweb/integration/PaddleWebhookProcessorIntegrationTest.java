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
import com.mathweb.service.impl.AccessGrantService;
import com.mathweb.service.impl.PaddleWebhookProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PaddleWebhookProcessorIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PRICE_ID = "pri_test_pass30";
    private static final String OTHER_PRICE_ID = "pri_someone_else";

    @Autowired PaddleWebhookProcessor processor;
    @Autowired AccessGrantService accessGrantService;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    private Plan pass30;

    @BeforeEach
    void setUp() {
        // poll() claims every due event in the shared database, so park leftovers
        // from other test classes first.
        jdbc.update("UPDATE paddle_webhook_events SET status = 'IGNORED' "
                + "WHERE status IN ('RECEIVED', 'RETRY', 'PROCESSING')");

        // The seed data has no Paddle price ID for the plan.
        jdbc.update("UPDATE plans SET paddle_price_id = ? WHERE code = 'PASS_30'", PRICE_ID);
        pass30 = planRepository.findByCodeAndActiveTrue("PASS_30").orElseThrow();
    }

    // ---------------------------------------------------------------
    // Happy paths
    // ---------------------------------------------------------------
    @Test
    void validTransaction_grantsAccess_completesOrder_marksEventProcessed() throws Exception {
        User user = createUser();
        String txn = newTxn();
        UUID orderId = createOrder(user, txn, Instant.now().plus(1, ChronoUnit.HOURS));
        String eventId = insertEvent("transaction.completed", validData(txn), Instant.now());

        processor.poll();

        assertThat(eventStatus(eventId)).isEqualTo("PROCESSED");
        assertThat(orderStatus(orderId)).isEqualTo("COMPLETED");
        assertThat(paymentCount(txn)).isEqualTo(1);

        Map<String, Object> order = jdbc.queryForMap(
                "SELECT actual_subtotal_cents, actual_total_cents, completed_at "
                        + "FROM paddle_checkout_orders WHERE id = ?", orderId);
        assertThat(order.get("actual_subtotal_cents")).isEqualTo(199L);
        assertThat(order.get("actual_total_cents")).isEqualTo(199L);
        assertThat(order.get("completed_at")).isNotNull();

        Subscription sub = subscriptionRepository.findByUser(user).orElseThrow();
        assertThat(sub.getCurrentPeriodEnd()).isCloseTo(
                LocalDateTime.now().plusDays(30), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void taxInclusiveTotal_isAccepted_andRecordedSeparatelyFromSubtotal() throws Exception {
        User user = createUser();
        String txn = newTxn();
        UUID orderId = createOrder(user, txn, Instant.now().plus(1, ChronoUnit.HOURS));

        ObjectNode data = validData(txn);
        ((ObjectNode) data.get("details").get("totals")).put("grand_total", "236");
        String eventId = insertEvent("transaction.completed", data, Instant.now());

        processor.poll();

        assertThat(eventStatus(eventId)).isEqualTo("PROCESSED");
        Map<String, Object> order = jdbc.queryForMap(
                "SELECT actual_subtotal_cents, actual_total_cents "
                        + "FROM paddle_checkout_orders WHERE id = ?", orderId);
        assertThat(order.get("actual_subtotal_cents")).isEqualTo(199L); // compared to plan price
        assertThat(order.get("actual_total_cents")).isEqualTo(236L);    // what the customer paid
        assertThat(jdbc.queryForObject(
                "SELECT amount_cents FROM payments WHERE provider_transaction_id = ?",
                Integer.class, txn)).isEqualTo(236);
    }

    // ---------------------------------------------------------------
    // 5. Invalid input never grants access
    // ---------------------------------------------------------------
    static Stream<Arguments> invalidTransactions() {
        return Stream.of(
                bad("status is not completed", "Transaction status is not completed",
                        d -> d.put("status", "billed")),
                bad("status missing", "Transaction status is not completed",
                        d -> d.remove("status")),
                bad("currency differs", "Currency mismatch",
                        d -> d.put("currency_code", "EUR")),
                bad("currency missing", "Currency mismatch",
                        d -> d.remove("currency_code")),
                bad("subtotal too low", "Subtotal mismatch",
                        d -> lineTotals(d).put("subtotal", "100")),
                bad("subtotal too high", "Subtotal mismatch",
                        d -> lineTotals(d).put("subtotal", "999")),
                bad("subtotal not a number", "Missing or invalid totals",
                        d -> lineTotals(d).put("subtotal", "abc")),
                bad("item price id differs", "Price ID mismatch",
                        d -> ((ObjectNode) firstItem(d).get("price")).put("id", OTHER_PRICE_ID)),
                bad("line item price id differs", "Price ID mismatch",
                        d -> firstLine(d).put("price_id", OTHER_PRICE_ID)),
                bad("quantity is 2", "Quantity must be explicitly 1",
                        d -> firstItem(d).put("quantity", 2)),
                bad("quantity missing (never defaults to 1)", "Quantity must be explicitly 1",
                        d -> firstItem(d).remove("quantity")),
                bad("line item quantity is 2", "Quantity must be explicitly 1",
                        d -> firstLine(d).put("quantity", 2)),
                bad("second item added", "Expected exactly one item",
                        d -> ((ArrayNode) d.get("items")).add(firstItem(d).deepCopy())),
                bad("line-item discount", "Unexpected discount",
                        d -> lineTotals(d).put("discount", "50")),
                bad("transaction discount", "Unexpected discount",
                        d -> ((ObjectNode) d.get("details").get("totals")).put("discount", "50")),
                bad("discount id present", "Unexpected discount",
                        d -> d.put("discount_id", "dsc_123")),
                bad("no line items", "Expected exactly one line item",
                        d -> ((ObjectNode) d.get("details")).putArray("line_items")),
                // Unknown transaction: may be a race with checkout creation, so it is retried.
                bad("unknown transaction", "No checkout order for transaction",
                        d -> d.put("id", "txn_other_" + UUID.randomUUID()))
        );
    }

    private static Arguments bad(String name, String expectedReason, Consumer<ObjectNode> mutator) {
        return Arguments.of(name, expectedReason, mutator);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTransactions")
    void invalidTransaction_isNotGranted_andRejectedForTheRightReason(
            String name, String expectedReason, Consumer<ObjectNode> mutator) throws Exception {

        User user = createUser();
        String txn = newTxn();
        UUID orderId = createOrder(user, txn, Instant.now().plus(1, ChronoUnit.HOURS));

        ObjectNode data = validData(txn);
        mutator.accept(data);
        String eventId = insertEvent("transaction.completed", data, Instant.now());

        processor.poll();

        // Never grants, whatever the reason.
        assertThat(paymentCount(txn)).isZero();
        assertThat(paymentCountForUser(user)).isZero();
        assertThat(subscriptionRepository.findByUser(user)).isEmpty();
        assertThat(orderStatus(orderId)).isEqualTo("READY");

        // Rejected by the intended rule, not by an unrelated one.
        assertThat(eventError(eventId)).contains(expectedReason);

        // Validation failures are dead-lettered at once; an unknown order is retried.
        String expectedStatus = name.equals("unknown transaction") ? "RETRY" : "DEAD_LETTER";
        assertThat(eventStatus(eventId)).isEqualTo(expectedStatus);
    }

    @Test
    void orderThatExpiredBeforePayment_isDeadLettered() throws Exception {
        User user = createUser();
        String txn = newTxn();
        UUID orderId = createOrder(user, txn, Instant.now().minus(1, ChronoUnit.HOURS));
        String eventId = insertEvent("transaction.completed", validData(txn), Instant.now());

        processor.poll();

        assertThat(eventStatus(eventId)).isEqualTo("DEAD_LETTER");
        assertThat(paymentCount(txn)).isZero();
        assertThat(orderStatus(orderId)).isEqualTo("READY");
    }

    @Test
    void unknownTransaction_isRetried_notDeadLettered_andGrantsNothing() throws Exception {
        User user = createUser();
        String eventId = insertEvent("transaction.completed", validData(newTxn()), Instant.now());

        processor.poll();

        Map<String, Object> row = eventRow(eventId);
        assertThat(row.get("status")).isEqualTo("RETRY");
        assertThat(row.get("attempt_count")).isEqualTo(1);
        assertThat(((Timestamp) row.get("next_attempt_at")).toInstant()).isAfter(Instant.now());
        assertThat(paymentCountForUser(user)).isZero();
    }

    // ---------------------------------------------------------------
    // 3. Duplicate transaction with different data is dead-lettered
    // ---------------------------------------------------------------
    @Test
    void existingPaymentWithDifferentAmount_isDeadLettered_andOrderStaysOpen() throws Exception {
        User user = createUser();
        String txn = newTxn();
        UUID orderId = createOrder(user, txn, Instant.now().plus(1, ChronoUnit.HOURS));

        // Same provider transaction ID already recorded with another amount.
        accessGrantService.grantAccess(user.getId(), pass30.getId(), "PADDLE", txn, 100, "USD");
        LocalDateTime endBefore = subscriptionRepository.findByUser(user).orElseThrow()
                .getCurrentPeriodEnd();

        String eventId = insertEvent("transaction.completed", validData(txn), Instant.now());

        processor.poll();

        assertThat(eventStatus(eventId)).isEqualTo("DEAD_LETTER");
        assertThat(eventError(eventId)).contains("already exists with different details");
        assertThat(orderStatus(orderId)).isEqualTo("READY");
        assertThat(paymentCount(txn)).isEqualTo(1);
        assertThat(subscriptionRepository.findByUser(user).orElseThrow().getCurrentPeriodEnd())
                .isEqualTo(endBefore);
    }

    @Test
    void existingPaymentForDifferentUser_isDeadLettered_andNoAccessGoesToOrderOwner() throws Exception {
        User owner = createUser();
        User orderUser = createUser();
        String txn = newTxn();
        UUID orderId = createOrder(orderUser, txn, Instant.now().plus(1, ChronoUnit.HOURS));

        accessGrantService.grantAccess(owner.getId(), pass30.getId(), "PADDLE", txn, 199, "USD");

        String eventId = insertEvent("transaction.completed", validData(txn), Instant.now());

        processor.poll();

        assertThat(eventStatus(eventId)).isEqualTo("DEAD_LETTER");
        assertThat(orderStatus(orderId)).isEqualTo("READY");
        assertThat(paymentCount(txn)).isEqualTo(1);
        assertThat(subscriptionRepository.findByUser(orderUser)).isEmpty();
    }

    @Test
    void existingIdenticalPayment_isAlreadyProcessed_orderCompleted_noSecondExtension() throws Exception {
        User user = createUser();
        String txn = newTxn();
        UUID orderId = createOrder(user, txn, Instant.now().plus(1, ChronoUnit.HOURS));

        // Simulates a crash after the payment insert but before the event was marked done.
        accessGrantService.grantAccess(user.getId(), pass30.getId(), "PADDLE", txn, 199, "USD");
        LocalDateTime endBefore = subscriptionRepository.findByUser(user).orElseThrow()
                .getCurrentPeriodEnd();

        String eventId = insertEvent("transaction.completed", validData(txn), Instant.now());

        processor.poll();

        assertThat(eventStatus(eventId)).isEqualTo("PROCESSED");
        assertThat(orderStatus(orderId)).isEqualTo("COMPLETED");
        assertThat(paymentCount(txn)).isEqualTo(1);
        assertThat(subscriptionRepository.findByUser(user).orElseThrow().getCurrentPeriodEnd())
                .isEqualTo(endBefore);
    }

    @Test
    void sameEventReprocessedAfterCompletion_isIgnored() throws Exception {
        User user = createUser();
        String txn = newTxn();
        UUID orderId = createOrder(user, txn, Instant.now().plus(1, ChronoUnit.HOURS));

        insertEvent("transaction.completed", validData(txn), Instant.now());
        processor.poll();
        LocalDateTime endAfterFirst = subscriptionRepository.findByUser(user).orElseThrow()
                .getCurrentPeriodEnd();

        // Paddle sends a second, distinct event for the same transaction.
        String secondEvent = insertEvent("transaction.completed", validData(txn), Instant.now());
        processor.poll();

        assertThat(eventStatus(secondEvent)).isEqualTo("IGNORED");
        assertThat(orderStatus(orderId)).isEqualTo("COMPLETED");
        assertThat(paymentCount(txn)).isEqualTo(1);
        assertThat(subscriptionRepository.findByUser(user).orElseThrow().getCurrentPeriodEnd())
                .isEqualTo(endAfterFirst);
    }

    // ---------------------------------------------------------------
    // Policy: adjustments never revoke access
    // ---------------------------------------------------------------
    @Test
    void adjustmentEvent_isIgnoredWithNote_andAccessIsUntouched() throws Exception {
        User user = createUser();
        String txn = newTxn();
        accessGrantService.grantAccess(user.getId(), pass30.getId(), "PADDLE", txn, 199, "USD");
        LocalDateTime endBefore = subscriptionRepository.findByUser(user).orElseThrow()
                .getCurrentPeriodEnd();

        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", "adj_" + UUID.randomUUID());
        data.put("transaction_id", txn);
        data.put("action", "refund");
        data.put("status", "approved");
        String eventId = insertEvent("adjustment.updated", data, Instant.now());

        processor.poll();

        assertThat(eventStatus(eventId)).isEqualTo("IGNORED");
        assertThat(eventError(eventId)).isEqualTo("Adjustment recorded, access not revoked by policy");
        assertThat(subscriptionRepository.findByUser(user).orElseThrow().getCurrentPeriodEnd())
                .isEqualTo(endBefore);
    }

    @Test
    void unrelatedEventType_isIgnored() throws Exception {
        String eventId = insertEvent("customer.updated",
                objectMapper.createObjectNode().put("id", "ctm_1"), Instant.now());

        processor.poll();

        assertThat(eventStatus(eventId)).isEqualTo("IGNORED");
    }

    // ---------------------------------------------------------------
    // Payload builders and mutators
    // ---------------------------------------------------------------

    private static ObjectNode firstItem(ObjectNode data) {
        return (ObjectNode) data.get("items").get(0);
    }

    private static ObjectNode firstLine(ObjectNode data) {
        return (ObjectNode) data.get("details").get("line_items").get(0);
    }

    private static ObjectNode lineTotals(ObjectNode data) {
        return (ObjectNode) firstLine(data).get("totals");
    }

    private ObjectNode validData(String txn) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", txn);
        data.put("status", "completed");
        data.put("currency_code", "USD");
        data.putNull("discount_id");

        ObjectNode item = data.putArray("items").addObject();
        item.putObject("price").put("id", PRICE_ID);
        item.put("quantity", 1);

        ObjectNode details = data.putObject("details");
        ObjectNode totals = details.putObject("totals");
        totals.put("discount", "0");
        totals.put("grand_total", "199");

        ObjectNode line = details.putArray("line_items").addObject();
        line.put("price_id", PRICE_ID);
        line.put("quantity", 1);
        ObjectNode lineTotals = line.putObject("totals");
        lineTotals.put("subtotal", "199");
        lineTotals.put("discount", "0");

        return data;
    }

    // ---------------------------------------------------------------
    // Database helpers
    // ---------------------------------------------------------------
    private String insertEvent(String type, ObjectNode data, Instant occurredAt) throws Exception {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");

        ObjectNode root = objectMapper.createObjectNode();
        root.put("event_id", eventId);
        root.put("notification_id", "ntf_" + UUID.randomUUID().toString().replace("-", ""));
        root.put("event_type", type);
        root.put("occurred_at", occurredAt.toString());
        root.set("data", data);

        jdbc.update("""
                INSERT INTO paddle_webhook_events
                    (event_id, notification_id, event_type, occurred_at, source,
                     payload, status, attempt_count, next_attempt_at, created_at)
                VALUES (?, ?, ?, ?, 'WEBHOOK', CAST(? AS jsonb), 'RECEIVED', 0, NOW(), NOW())
                """,
                eventId, root.get("notification_id").asText(), type,
                Timestamp.from(occurredAt), objectMapper.writeValueAsString(root));
        return eventId;
    }

    private UUID createOrder(User user, String txn, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO paddle_checkout_orders
                    (id, user_id, plan_id, expected_price_id, expected_subtotal_cents,
                     expected_currency, paddle_transaction_id, status, expires_at)
                VALUES (?, ?, ?, ?, ?, 'USD', ?, 'READY', ?)
                """,
                id, user.getId(), pass30.getId(), PRICE_ID,
                (long) pass30.getPriceCents(), txn, Timestamp.from(expiresAt));
        return id;
    }

    private Map<String, Object> eventRow(String eventId) {
        return jdbc.queryForMap(
                "SELECT status, last_error, attempt_count, next_attempt_at "
                        + "FROM paddle_webhook_events WHERE event_id = ?", eventId);
    }

    private String eventStatus(String eventId) {
        return (String) eventRow(eventId).get("status");
    }

    private String eventError(String eventId) {
        return (String) eventRow(eventId).get("last_error");
    }

    private String orderStatus(UUID orderId) {
        return jdbc.queryForObject(
                "SELECT status FROM paddle_checkout_orders WHERE id = ?", String.class, orderId);
    }

    private int paymentCount(String txn) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE provider_transaction_id = ?",
                Integer.class, txn);
    }

    private int paymentCountForUser(User user) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE user_id = ?", Integer.class, user.getId());
    }

    private static String newTxn() {
        return "txn_" + UUID.randomUUID();
    }

    private User createUser() {
        return userRepository.saveAndFlush(
                User.builder()
                        .email("it-" + UUID.randomUUID() + "@example.test")
                        .firstName("Test")
                        .lastName("User")
                        .passwordHash("not-used")
                        .role(Role.STUDENT)   // same line that works in AccessGrantConcurrencyIntegrationTest
                        .build());
    }
}