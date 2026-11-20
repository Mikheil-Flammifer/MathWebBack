package com.mathweb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathweb.security.PaddleSignatureVerifier;
import com.mathweb.service.impl.PaddleWebhookInboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaddleWebhookControllerTest {

    private static final String SIGNATURE =
            "ts=1700000000;h1=abc";

    private static final String EVENT_ID =
            "evt_01j123456789abcdefghijklm";

    private static final String NOTIFICATION_ID =
            "ntf_01j123456789abcdefghijklm";

    private static final String OCCURRED_AT =
            "2026-11-20T10:15:30.123Z";

    @Mock
    private PaddleSignatureVerifier signatureVerifier;

    @Mock
    private PaddleWebhookInboxService inboxService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PaddleWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new PaddleWebhookController(signatureVerifier, inboxService, objectMapper);

        lenient().when(signatureVerifier.verify(
                nullable(String.class),
                any(byte[].class)
        )).thenReturn(true);
    }

    private ObjectNode validEvent() {
        ObjectNode root = objectMapper.createObjectNode();

        root.put("event_id", EVENT_ID);
        root.put("notification_id", NOTIFICATION_ID);
        root.put("event_type", "transaction.completed");
        root.put("occurred_at", OCCURRED_AT);

        ObjectNode data = root.putObject("data");
        data.put("id", "txn_01j123456789abcdefghijklm");
        data.put("status", "completed");
        data.put("currency_code", "USD");

        ObjectNode item = data.putArray("items").addObject();
        item.putObject("price")
                .put("id", "pri_01j123456789abcdefghijklm");
        item.put("quantity", 1);

        return root;
    }

    private ResponseEntity<Void> post(ObjectNode event) throws Exception {
        return controller.handle(
                SIGNATURE,
                objectMapper.writeValueAsBytes(event)
        );
    }

    private ResponseEntity<Void> post(String rawBody) {
        return controller.handle(
                SIGNATURE,
                rawBody.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static int status(ResponseEntity<?> response) {
        return response.getStatusCode().value();
    }

    @Nested
    class SignatureVerification {

        @Test
        void invalidSignature_returns401_andDoesNotStoreEvent() {
            when(signatureVerifier.verify(
                    anyString(),
                    any(byte[].class)
            )).thenReturn(false);

            ResponseEntity<Void> response = post("not even json");

            assertThat(status(response)).isEqualTo(401);
            verifyNoInteractions(inboxService);
        }

        @Test
        void missingSignature_returns401_andDoesNotStoreEvent()
                throws Exception {

            when(signatureVerifier.verify(
                    isNull(),
                    any(byte[].class)
            )).thenReturn(false);

            byte[] body =
                    objectMapper.writeValueAsBytes(validEvent());

            ResponseEntity<Void> response =
                    controller.handle(null, body);

            assertThat(status(response)).isEqualTo(401);
            verifyNoInteractions(inboxService);
        }

        @Test
        void verifierReceivesOriginalHeaderAndRawBody()
                throws Exception {

            byte[] body =
                    objectMapper.writeValueAsBytes(validEvent());

            controller.handle("ts=1;h1=test", body);

            verify(signatureVerifier)
                    .verify("ts=1;h1=test", body);
        }
    }

    @Nested
    class EnvelopeValidation {

        @ParameterizedTest
        @ValueSource(strings = {
                "not json",
                "{\"event_type\":",
                "",
                "null"
        })
        void malformedJson_returns400_andDoesNotStore(String raw) {
            assertThat(status(post(raw))).isEqualTo(400);

            verifyNoInteractions(inboxService);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "[]",
                "\"text\"",
                "123",
                "true"
        })
        void nonObjectJson_returns400(String raw) {
            assertThat(status(post(raw))).isEqualTo(400);

            verifyNoInteractions(inboxService);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "event_id",
                "event_type",
                "occurred_at"
        })
        void missingRequiredEnvelopeField_returns400(String field)
                throws Exception {

            ObjectNode event = validEvent();
            event.remove(field);

            assertThat(status(post(event))).isEqualTo(400);
            verifyNoInteractions(inboxService);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "event_id",
                "event_type",
                "occurred_at"
        })
        void blankRequiredEnvelopeField_returns400(String field)
                throws Exception {

            ObjectNode event = validEvent();
            event.put(field, "   ");

            assertThat(status(post(event))).isEqualTo(400);
            verifyNoInteractions(inboxService);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "event_id",
                "event_type",
                "occurred_at"
        })
        void nonTextRequiredEnvelopeField_returns400(String field)
                throws Exception {

            ObjectNode event = validEvent();
            event.put(field, 123);

            assertThat(status(post(event))).isEqualTo(400);
            verifyNoInteractions(inboxService);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "not-a-date",
                "2026-99-99",
                "20 November 2026"
        })
        void invalidOccurredAt_returns400(String occurredAt)
                throws Exception {

            ObjectNode event = validEvent();
            event.put("occurred_at", occurredAt);

            assertThat(status(post(event))).isEqualTo(400);
            verifyNoInteractions(inboxService);
        }

        @Test
        void missingNotificationId_isAcceptedAndStoredAsNull()
                throws Exception {

            ObjectNode event = validEvent();
            event.remove("notification_id");

            String rawPayload =
                    objectMapper.writeValueAsString(event);

            assertThat(status(post(rawPayload))).isEqualTo(200);

            verify(inboxService).store(
                    EVENT_ID,
                    null,
                    "transaction.completed",
                    Instant.parse(OCCURRED_AT),
                    rawPayload
            );
        }

        @Test
        void blankNotificationId_returns400() throws Exception {
            ObjectNode event = validEvent();
            event.put("notification_id", " ");

            assertThat(status(post(event))).isEqualTo(400);
            verifyNoInteractions(inboxService);
        }

        @Test
        void nonTextNotificationId_returns400() throws Exception {
            ObjectNode event = validEvent();
            event.put("notification_id", 123);

            assertThat(status(post(event))).isEqualTo(400);
            verifyNoInteractions(inboxService);
        }
    }

    @Nested
    class EventPersistence {

        @Test
        void validEvent_isStoredAndAcknowledged()
                throws Exception {

            ObjectNode event = validEvent();
            String rawPayload =
                    objectMapper.writeValueAsString(event);

            when(inboxService.store(
                    anyString(),
                    anyString(),
                    anyString(),
                    any(Instant.class),
                    anyString()
            )).thenReturn(true);

            ResponseEntity<Void> response = post(rawPayload);

            assertThat(status(response)).isEqualTo(200);

            verify(inboxService).store(
                    EVENT_ID,
                    NOTIFICATION_ID,
                    "transaction.completed",
                    Instant.parse(OCCURRED_AT),
                    rawPayload
            );
        }

        @Test
        void rawPayload_isStoredWithoutReSerialization() {
            String rawPayload = """
                    {
                      "event_id": "%s",
                      "notification_id": "%s",
                      "event_type": "transaction.completed",
                      "occurred_at": "%s",
                      "data": { "id": "txn_1" }
                    }
                    """.formatted(
                    EVENT_ID,
                    NOTIFICATION_ID,
                    OCCURRED_AT
            );

            ResponseEntity<Void> response = post(rawPayload);

            assertThat(status(response)).isEqualTo(200);

            verify(inboxService).store(
                    EVENT_ID,
                    NOTIFICATION_ID,
                    "transaction.completed",
                    Instant.parse(OCCURRED_AT),
                    rawPayload
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "transaction.completed",
                "transaction.updated",
                "adjustment.created",
                "adjustment.updated",
                "subscription.created",
                "customer.updated"
        })
        void everyValidEventType_isPersisted(String eventType)
                throws Exception {

            ObjectNode event = validEvent();
            event.put("event_type", eventType);

            String rawPayload =
                    objectMapper.writeValueAsString(event);

            assertThat(status(post(rawPayload))).isEqualTo(200);

            verify(inboxService).store(
                    EVENT_ID,
                    NOTIFICATION_ID,
                    eventType,
                    Instant.parse(OCCURRED_AT),
                    rawPayload
            );
        }

        @Test
        void transactionContents_areNotProcessedByController()
                throws Exception {

            ObjectNode event = validEvent();

            // Deliberately malformed transaction contents.
            event.remove("data");

            String rawPayload =
                    objectMapper.writeValueAsString(event);

            assertThat(status(post(rawPayload))).isEqualTo(200);

            verify(inboxService).store(
                    EVENT_ID,
                    NOTIFICATION_ID,
                    "transaction.completed",
                    Instant.parse(OCCURRED_AT),
                    rawPayload
            );
        }

        @Test
        void duplicateEvent_isAcknowledged() throws Exception {
            ObjectNode event = validEvent();

            when(inboxService.store(
                    anyString(),
                    anyString(),
                    anyString(),
                    any(Instant.class),
                    anyString()
            )).thenReturn(false);

            assertThat(status(post(event))).isEqualTo(200);

            verify(inboxService).store(
                    eq(EVENT_ID),
                    eq(NOTIFICATION_ID),
                    eq("transaction.completed"),
                    eq(Instant.parse(OCCURRED_AT)),
                    anyString()
            );
        }
    }

    @Nested
    class DatabaseFailures {

        @Test
        void databaseFailure_isNotSwallowed() throws Exception {
            ObjectNode event = validEvent();

            when(inboxService.store(
                    anyString(),
                    anyString(),
                    anyString(),
                    any(Instant.class),
                    anyString()
            )).thenThrow(
                    new DataAccessResourceFailureException(
                            "database unavailable"
                    )
            );

            assertThatThrownBy(() -> post(event))
                    .isInstanceOf(
                            DataAccessResourceFailureException.class
                    )
                    .hasMessageContaining("database unavailable");
        }

        @Test
        void unexpectedFailure_isNotSwallowed() throws Exception {
            ObjectNode event = validEvent();

            when(inboxService.store(
                    anyString(),
                    anyString(),
                    anyString(),
                    any(Instant.class),
                    anyString()
            )).thenThrow(
                    new IllegalStateException("unexpected failure")
            );

            assertThatThrownBy(() -> post(event))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unexpected failure");
        }
    }
}