package com.mathweb.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathweb.entity.Plan;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.PlanRepository;
import com.mathweb.security.PaddleSignatureVerifier;
import com.mathweb.service.AccessGrantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/paddle")
public class PaddleWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaddleWebhookController.class);

    private final PaddleSignatureVerifier verifier;
    private final PlanRepository planRepository;
    private final AccessGrantService accessGrantService;
    private final ObjectMapper objectMapper;

    public PaddleWebhookController(PaddleSignatureVerifier verifier,
                                   PlanRepository planRepository,
                                   AccessGrantService accessGrantService,
                                   ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.planRepository = planRepository;
        this.accessGrantService = accessGrantService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handle(
            @RequestHeader(value = "Paddle-Signature", required = false) String signature,
            @RequestBody byte[] body) {

        if (!verifier.verify(signature, body)) {
            log.warn("Paddle webhook rejected: invalid signature");
            return ResponseEntity.status(401).build();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        if (!"transaction.completed".equals(root.path("event_type").asText())) {
            return ResponseEntity.ok().build();   // other events are ignored
        }

        JsonNode data = root.path("data");
        String txnId = data.path("id").asText("");
        JsonNode items = data.path("items");

        if (txnId.isBlank() || !items.isArray() || items.size() != 1) {
            log.error("Paddle transaction {} ignored: unexpected shape (items != 1)", txnId);
            return ResponseEntity.ok().build();
        }

        JsonNode item = items.get(0);
        String priceId = item.path("price").path("id").asText(item.path("price_id").asText(""));
        int quantity = item.path("quantity").asInt(1);

        Plan plan = planRepository.findByPaddlePriceIdAndActiveTrue(priceId).orElse(null);
        if (plan == null || quantity != 1) {
            log.error("Paddle transaction {} ignored: unknown price '{}' or quantity {}", txnId, priceId, quantity);
            return ResponseEntity.ok().build();
        }

        long userId = data.path("custom_data").path("userId").asLong(0);
        if (userId <= 0) {
            log.error("Paddle transaction {} PAID but has no userId in custom_data: handle manually", txnId);
            return ResponseEntity.ok().build();
        }

        int amountCents;
        try {
            amountCents = Integer.parseInt(data.path("details").path("totals").path("grand_total").asText(""));
        } catch (NumberFormatException e) {
            log.error("Paddle transaction {} has no readable grand_total", txnId);
            return ResponseEntity.ok().build();
        }
        String currency = data.path("currency_code").asText("USD");

        try {
            accessGrantService.grantAccess(userId, plan.getId(), "PADDLE", txnId, amountCents, currency);
        } catch (DataIntegrityViolationException e) {
            log.info("Paddle transaction {} already recorded (concurrent duplicate)", txnId);
        } catch (ResourceNotFoundException e) {
            log.error("Paddle transaction {} PAID but user {} not found: handle manually", txnId, userId);
        }
        return ResponseEntity.ok().build();
    }
}