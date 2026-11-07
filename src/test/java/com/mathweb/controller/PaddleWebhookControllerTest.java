package com.mathweb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathweb.entity.Plan;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.PlanRepository;
import com.mathweb.security.PaddleSignatureVerifier;
import com.mathweb.service.impl.AccessGrantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaddleWebhookController}.
 * The signature verifier, plan repository and AccessGrantService are mocked,
 * so these tests check only the controller's own decisions: what it accepts,
 * what it ignores, and exactly what it hands to AccessGrantService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaddleWebhookControllerTest {

    private static final String SIGNATURE = "ts=1700000000;h1=abc";

    @Mock private PaddleSignatureVerifier verifier;
    @Mock private PlanRepository planRepository;
    @Mock private AccessGrantService accessGrantService;

    private final ObjectMapper mapper = new ObjectMapper();
    private PaddleWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new PaddleWebhookController(verifier, planRepository, accessGrantService, mapper);
        when(verifier.verify(any(), any())).thenReturn(true);   // valid signature unless a test says otherwise
    }

    // ==================== HELPERS ====================

    /** A complete, valid "transaction.completed" event. Tests change one thing at a time. */
    private ObjectNode validEvent() {
        ObjectNode root = mapper.createObjectNode();
        root.put("event_type", "transaction.completed");
        ObjectNode data = root.putObject("data");
        data.put("id", "txn_1");
        data.put("currency_code", "EUR");
        ObjectNode item = data.putArray("items").addObject();
        item.putObject("price").put("id", "pri_monthly");
        item.put("quantity", 1);
        data.putObject("custom_data").put("userId", 42);
        data.putObject("details").putObject("totals").put("grand_total", "1000");
        return root;
    }

    private static ObjectNode data(ObjectNode event) {
        return (ObjectNode) event.get("data");
    }

    private static ArrayNode items(ObjectNode event) {
        return (ArrayNode) data(event).get("items");
    }

    private static ObjectNode item(ObjectNode event) {
        return (ObjectNode) items(event).get(0);
    }

    private static ObjectNode totals(ObjectNode event) {
        return (ObjectNode) data(event).at("/details/totals");
    }

    /** Makes the plan "pri_monthly" exist (id 7). */
    private Plan planExists() {
        Plan plan = Plan.builder()
                .code("monthly").name("Monthly").durationDays(30).priceCents(1000)
                .build();
        plan.setId(7L);
        when(planRepository.findByPaddlePriceIdAndActiveTrue("pri_monthly")).thenReturn(Optional.of(plan));
        return plan;
    }

    private ResponseEntity<Void> post(ObjectNode event) throws Exception {
        return controller.handle(SIGNATURE, mapper.writeValueAsBytes(event));
    }

    private ResponseEntity<Void> post(String rawBody) {
        return controller.handle(SIGNATURE, rawBody.getBytes(StandardCharsets.UTF_8));
    }

    private static int status(ResponseEntity<?> response) {
        return response.getStatusCode().value();
    }

    /** Paddle must get 200 (so it stops retrying) but nobody gets access. */
    private void assertAcknowledgedButNothingGranted(ObjectNode event) throws Exception {
        assertThat(status(post(event))).isEqualTo(200);
        verifyNoInteractions(accessGrantService);
    }

    private void verifyAccessGranted() {
        verify(accessGrantService).grantAccess(42L, 7L, "PADDLE", "txn_1", 1000, "EUR");
    }

    // ==================== SIGNATURE ====================

    @Nested
    class Signature {

        @Test
        void invalidSignature_returns401_andNothingElseHappens() throws Exception {
            when(verifier.verify(any(), any())).thenReturn(false);

            assertThat(status(post(validEvent()))).isEqualTo(401);

            verifyNoInteractions(planRepository, accessGrantService);
        }

        @Test
        void missingSignatureHeader_returns401() throws Exception {
            when(verifier.verify(isNull(), any())).thenReturn(false);

            ResponseEntity<Void> response = controller.handle(null, mapper.writeValueAsBytes(validEvent()));

            assertThat(status(response)).isEqualTo(401);
            verifyNoInteractions(planRepository, accessGrantService);
        }

        @Test
        void verifierReceivesTheHeaderAndTheRawBody() throws Exception {
            planExists();
            byte[] body = mapper.writeValueAsBytes(validEvent());

            controller.handle("ts=1;h1=abc", body);

            verify(verifier).verify("ts=1;h1=abc", body);
        }
    }

    // ==================== BODY PARSING ====================

    @Nested
    class Parsing {

        @ParameterizedTest
        @ValueSource(strings = {"not json", "{\"event_type\":"})
        void malformedJson_returns400(String raw) {
            assertThat(status(post(raw))).isEqualTo(400);

            verifyNoInteractions(planRepository, accessGrantService);
        }

        @ParameterizedTest
        @ValueSource(strings = {"transaction.created", "transaction.updated", "transaction.paid",
                "subscription.created", "adjustment.created", ""})
        void otherEventTypes_areAcknowledged_andIgnored(String eventType) throws Exception {
            ObjectNode event = validEvent();
            event.put("event_type", eventType);

            assertThat(status(post(event))).isEqualTo(200);

            verifyNoInteractions(planRepository, accessGrantService);
        }

        @ParameterizedTest
        @ValueSource(strings = {"{}", "[]", "{\"data\":{}}"})
        void bodiesWithoutAnEventType_areAcknowledged_andIgnored(String raw) {
            assertThat(status(post(raw))).isEqualTo(200);

            verifyNoInteractions(planRepository, accessGrantService);
        }
    }

    // ==================== UNEXPECTED SHAPE ====================

    @Nested
    class UnexpectedShape {

        private void assertIgnoredBeforeAnyLookup(ObjectNode event) throws Exception {
            assertAcknowledgedButNothingGranted(event);
            verifyNoInteractions(planRepository);
        }

        @Test
        void missingTransactionId_isIgnored() throws Exception {
            ObjectNode event = validEvent();
            data(event).remove("id");

            assertIgnoredBeforeAnyLookup(event);
        }

        @Test
        void blankTransactionId_isIgnored() throws Exception {
            ObjectNode event = validEvent();
            data(event).put("id", "   ");

            assertIgnoredBeforeAnyLookup(event);
        }

        @Test
        void missingItems_isIgnored() throws Exception {
            ObjectNode event = validEvent();
            data(event).remove("items");

            assertIgnoredBeforeAnyLookup(event);
        }

        @Test
        void emptyItems_isIgnored() throws Exception {
            ObjectNode event = validEvent();
            data(event).putArray("items");

            assertIgnoredBeforeAnyLookup(event);
        }

        @Test
        void moreThanOneItem_isIgnored() throws Exception {
            ObjectNode event = validEvent();
            items(event).addObject().put("quantity", 1);

            assertIgnoredBeforeAnyLookup(event);
        }

        @Test
        void itemsThatAreNotAnArray_isIgnored() throws Exception {
            ObjectNode event = validEvent();
            data(event).putObject("items");

            assertIgnoredBeforeAnyLookup(event);
        }
    }

    // ==================== PLAN AND QUANTITY ====================

    @Nested
    class PlanAndQuantity {

        @Test
        void unknownPrice_isIgnored() throws Exception {
            when(planRepository.findByPaddlePriceIdAndActiveTrue("pri_monthly")).thenReturn(Optional.empty());

            assertAcknowledgedButNothingGranted(validEvent());

            verify(planRepository).findByPaddlePriceIdAndActiveTrue("pri_monthly");
        }

        @Test
        void itemWithoutAPrice_isIgnored() throws Exception {
            planExists();
            ObjectNode event = validEvent();
            item(event).remove("price");

            assertAcknowledgedButNothingGranted(event);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 2, 5, -1})
        void quantityOtherThanOne_isIgnored(int quantity) throws Exception {
            planExists();
            ObjectNode event = validEvent();
            item(event).put("quantity", quantity);

            assertAcknowledgedButNothingGranted(event);
        }

        @Test
        void missingQuantity_defaultsToOne() throws Exception {
            planExists();
            ObjectNode event = validEvent();
            item(event).remove("quantity");

            assertThat(status(post(event))).isEqualTo(200);

            verifyAccessGranted();
        }

        @Test
        void priceGivenAsLegacyPriceIdField_isAccepted() throws Exception {
            planExists();
            ObjectNode event = validEvent();
            item(event).remove("price");
            item(event).put("price_id", "pri_monthly");

            assertThat(status(post(event))).isEqualTo(200);

            verifyAccessGranted();
        }
    }

    // ==================== USER ID ====================

    @Nested
    class UserId {

        @Test
        void missingCustomData_isIgnored() throws Exception {
            planExists();
            ObjectNode event = validEvent();
            data(event).remove("custom_data");

            assertAcknowledgedButNothingGranted(event);
        }

        @Test
        void customDataWithoutUserId_isIgnored() throws Exception {
            planExists();
            ObjectNode event = validEvent();
            data(event).putObject("custom_data").put("somethingElse", "x");

            assertAcknowledgedButNothingGranted(event);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -42})
        void zeroOrNegativeUserId_isIgnored(int userId) throws Exception {
            planExists();
            ObjectNode event = validEvent();
            data(event).putObject("custom_data").put("userId", userId);

            assertAcknowledgedButNothingGranted(event);
        }

        @Test
        void nonNumericUserId_isIgnored() throws Exception {
            planExists();
            ObjectNode event = validEvent();
            data(event).putObject("custom_data").put("userId", "abc");

            assertAcknowledgedButNothingGranted(event);
        }

        @Test
        void userIdSentAsText_isAccepted() throws Exception {
            planExists();
            ObjectNode event = validEvent();
            data(event).putObject("custom_data").put("userId", "42");

            assertThat(status(post(event))).isEqualTo(200);

            verifyAccessGranted();
        }
    }

    // ==================== AMOUNT ====================

    @Nested
    class Amount {

        @Test
        void missingGrandTotal_isIgnored() throws Exception {
            planExists();
            ObjectNode event = validEvent();
            totals(event).remove("grand_total");

            assertAcknowledgedButNothingGranted(event);
        }

        @ParameterizedTest
        @ValueSource(strings = {"12.50", "abc", "", "1e3", "99999999999"})
        void unreadableGrandTotal_isIgnored(String grandTotal) throws Exception {
            planExists();
            ObjectNode event = validEvent();
            totals(event).put("grand_total", grandTotal);

            assertAcknowledgedButNothingGranted(event);
        }

        @Test
        void grandTotalSentAsJsonNumber_isAccepted() throws Exception {
            planExists();
            ObjectNode event = validEvent();
            totals(event).put("grand_total", 2500);

            assertThat(status(post(event))).isEqualTo(200);

            verify(accessGrantService).grantAccess(42L, 7L, "PADDLE", "txn_1", 2500, "EUR");
        }
    }

    // ==================== GRANTING ACCESS ====================

    @Nested
    class GrantingAccess {

        @Test
        void validTransaction_grantsAccessOnce_withExactValues() throws Exception {
            planExists();

            assertThat(status(post(validEvent()))).isEqualTo(200);

            verifyAccessGranted();
            verifyNoMoreInteractions(accessGrantService);
        }

        @Test
        void missingCurrency_defaultsToUsd() throws Exception {
            planExists();
            ObjectNode event = validEvent();
            data(event).remove("currency_code");

            assertThat(status(post(event))).isEqualTo(200);

            verify(accessGrantService).grantAccess(42L, 7L, "PADDLE", "txn_1", 1000, "USD");
        }
    }

    // ==================== FAILURES FROM AccessGrantService ====================

    @Nested
    class Failures {

        private void grantAccessThrows(Throwable error) {
            doThrow(error).when(accessGrantService)
                    .grantAccess(anyLong(), anyLong(), anyString(), anyString(), anyInt(), anyString());
        }

        @Test
        void duplicateTransaction_isAcknowledged() throws Exception {
            planExists();
            grantAccessThrows(new DataIntegrityViolationException("duplicate provider_transaction_id"));

            assertThat(status(post(validEvent()))).isEqualTo(200);
        }

        @Test
        void unknownUser_isAcknowledged() throws Exception {
            planExists();
            grantAccessThrows(new ResourceNotFoundException("User", 42L));

            assertThat(status(post(validEvent()))).isEqualTo(200);
        }

        @Test
        void unexpectedError_isNotSwallowed_soPaddleRetries() {
            planExists();
            grantAccessThrows(new IllegalStateException("database is down"));

            assertThatThrownBy(() -> post(validEvent()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}