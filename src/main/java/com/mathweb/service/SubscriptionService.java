package com.mathweb.service;

import com.mathweb.dto.request.CreateSubscriptionRequest;
import com.mathweb.dto.response.SubscriptionResponse;

public interface SubscriptionService {
    String createSubscription(Long userId);
    SubscriptionResponse captureSubscription(String orderId, Long userId);
    SubscriptionResponse getMySubscription(Long userId);
    void cancelSubscription(Long userId);
}