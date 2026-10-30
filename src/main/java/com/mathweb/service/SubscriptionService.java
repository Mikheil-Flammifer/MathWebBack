package com.mathweb.service;

import com.mathweb.dto.response.SubscriptionResponse;

public interface SubscriptionService {
    SubscriptionResponse getMySubscription(Long userId);
}