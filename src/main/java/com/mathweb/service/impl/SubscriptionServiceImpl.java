package com.mathweb.service.impl;

import com.mathweb.dto.request.CreateSubscriptionRequest;
import com.mathweb.dto.response.SubscriptionResponse;
import com.mathweb.entity.Subscription;
import com.mathweb.entity.User;
import com.mathweb.enums.SubscriptionStatus;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.SubscriptionRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.service.EmailService;
import com.mathweb.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionServiceImpl.class);

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public SubscriptionServiceImpl(SubscriptionRepository subscriptionRepository,
                                   UserRepository userRepository,
                                   EmailService emailService) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse getMySubscription(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Subscription subscription = subscriptionRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No subscription found for this user"));

        return mapToResponse(subscription);
    }

    @Override
    @Transactional
    public SubscriptionResponse createSubscription(CreateSubscriptionRequest request,
                                                   Long userId) {
        // Full Stripe implementation comes in the Stripe step
        // This is a placeholder so the app starts
        throw new UnsupportedOperationException(
                "Stripe integration not yet configured");
    }

    @Override
    @Transactional
    public void cancelSubscription(Long userId) {
        // Full Stripe implementation comes in the Stripe step
        throw new UnsupportedOperationException(
                "Stripe integration not yet configured");
    }

    @Override
    public void handleStripeWebhook(String payload, String sigHeader) {
        // Full Stripe implementation comes in the Stripe step
        throw new UnsupportedOperationException(
                "Stripe integration not yet configured");
    }

    // ===== PRIVATE HELPERS =====

    private SubscriptionResponse mapToResponse(Subscription subscription) {
        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .status(subscription.getStatus())
                .amountCents(subscription.getAmountCents())
                .currency(subscription.getCurrency())
                .currentPeriodStart(subscription.getCurrentPeriodStart())
                .currentPeriodEnd(subscription.getCurrentPeriodEnd())
                .cancelledAt(subscription.getCancelledAt())
                .isActive(subscription.isCurrentlyActive())
                .build();
    }
}