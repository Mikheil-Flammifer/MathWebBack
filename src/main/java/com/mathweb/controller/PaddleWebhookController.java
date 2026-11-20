package com.mathweb.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathweb.security.PaddleSignatureVerifier;
import com.mathweb.service.impl.PaddleWebhookInboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/paddle")
public class PaddleWebhookController {

    private static final Logger log =
            LoggerFactory.getLogger(PaddleWebhookController.class);

    private final PaddleSignatureVerifier signatureVerifier;
    private final PaddleWebhookInboxService inboxService;
    private final ObjectMapper objectMapper;

    public PaddleWebhookController(
            PaddleSignatureVerifier signatureVerifier,
            PaddleWebhookInboxService inboxService,
            ObjectMapper objectMapper
    ) {
        this.signatureVerifier = signatureVerifier;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handle(
            @RequestHeader(
                    value = "Paddle-Signature",
                    required = false
            ) String signature,
            @RequestBody byte[] body
    ) {
        if (!signatureVerifier.verify(signature, body)) {
            log.warn("Paddle webhook rejected: invalid signature");
            return ResponseEntity.status(401).build();
        }

        PaddleEnvelope envelope;

        try {
            envelope = parseEnvelope(body);
        } catch (IOException
                 | IllegalArgumentException
                 | DateTimeParseException exception) {
            log.warn(
                    "Paddle webhook rejected: malformed envelope: {}",
                    exception.getMessage()
            );
            return ResponseEntity.badRequest().build();
        }

        String rawPayload = new String(body, StandardCharsets.UTF_8);

        /*
         * Do not catch persistence exceptions here.
         *
         * Duplicate event IDs are handled by ON CONFLICT DO NOTHING.
         * Other database failures propagate and become HTTP 5xx, causing
         * Paddle to retry delivery.
         */
        inboxService.store(
                envelope.eventId(),
                envelope.notificationId(),
                envelope.eventType(),
                envelope.occurredAt(),
                rawPayload
        );

        return ResponseEntity.ok().build();
    }

    private PaddleEnvelope parseEnvelope(byte[] body) throws IOException {
        JsonNode root = objectMapper.readTree(body);

        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException(
                    "Webhook body must be a JSON object"
            );
        }

        String eventId = requiredText(root, "event_id");
        String eventType = requiredText(root, "event_type");
        String occurredAtValue = requiredText(root, "occurred_at");
        String notificationId = optionalText(root, "notification_id");

        Instant occurredAt = OffsetDateTime
                .parse(occurredAtValue)
                .toInstant();

        return new PaddleEnvelope(
                eventId,
                notificationId,
                eventType,
                occurredAt
        );
    }

    private String requiredText(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);

        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Missing or invalid " + fieldName
            );
        }

        return value.asText();
    }

    private String optionalText(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);

        if (value == null || value.isNull()) {
            return null;
        }

        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid " + fieldName
            );
        }

        return value.asText();
    }

    private record PaddleEnvelope(
            String eventId,
            String notificationId,
            String eventType,
            Instant occurredAt
    ) {
    }
}