package com.mathweb.service;

import com.mathweb.dto.request.CreateSubscriptionRequest;
import com.mathweb.dto.response.SubscriptionResponse;

public interface SubscriptionService {
    SubscriptionResponse createSubscription(CreateSubscriptionRequest request, Long userId);
    SubscriptionResponse getMySubscription(Long userId);
    void cancelSubscription(Long userId);
    void handleStripeWebhook(String payload, String sigHeader);
}