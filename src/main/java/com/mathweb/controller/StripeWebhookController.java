package com.mathweb.controller;

import com.mathweb.dto.response.ApiResponse;
import com.mathweb.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stripe")
public class StripeWebhookController {

    private final SubscriptionService subscriptionService;

    public StripeWebhookController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Void>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        subscriptionService.handleStripeWebhook(payload, sigHeader);
        return ResponseEntity.ok(ApiResponse.success("Webhook processed"));
    }
}