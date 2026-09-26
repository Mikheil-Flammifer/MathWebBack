package com.mathweb.controller;

import com.mathweb.dto.request.CreateSubscriptionRequest;
import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.SubscriptionResponse;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.SubscriptionService;
import jakarta.validation.Valid;
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

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getMySubscription(
            @AuthenticationPrincipal UserPrincipal principal) {
        SubscriptionResponse subscription =
                subscriptionService.getMySubscription(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Subscription retrieved", subscription));
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        SubscriptionResponse subscription =
                subscriptionService.createSubscription(request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Subscription created", subscription));
    }

    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelSubscription(
            @AuthenticationPrincipal UserPrincipal principal) {
        subscriptionService.cancelSubscription(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Subscription cancelled"));
    }
}