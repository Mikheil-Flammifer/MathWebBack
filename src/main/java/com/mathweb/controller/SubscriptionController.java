package com.mathweb.controller;

import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.SubscriptionResponse;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    // Step 1 — create PayPal order, returns approval URL
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<String>> createSubscription(
            @AuthenticationPrincipal UserPrincipal principal) {
        String approvalUrl = subscriptionService.createSubscription(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(
                "Redirect user to this PayPal URL", approvalUrl));
    }

    // Step 2 — called after user approves on PayPal
    @GetMapping("/capture")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> captureSubscription(
            @RequestParam("token") String orderId,
            @AuthenticationPrincipal UserPrincipal principal) {
        SubscriptionResponse subscription =
                subscriptionService.captureSubscription(orderId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription activated successfully", subscription));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getMySubscription(
            @AuthenticationPrincipal UserPrincipal principal) {
        SubscriptionResponse subscription =
                subscriptionService.getMySubscription(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Subscription retrieved", subscription));
    }

    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelSubscription(
            @AuthenticationPrincipal UserPrincipal principal) {
        subscriptionService.cancelSubscription(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Subscription cancelled"));
    }
}