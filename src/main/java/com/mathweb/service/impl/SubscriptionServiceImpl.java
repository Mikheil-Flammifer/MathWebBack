package com.mathweb.service.impl;

import com.mathweb.dto.request.CreateSubscriptionRequest;
import com.mathweb.dto.response.SubscriptionResponse;
import com.mathweb.entity.Subscription;
import com.mathweb.entity.User;
import com.mathweb.enums.SubscriptionStatus;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.exception.SubscriptionException;
import com.mathweb.mapper.SubscriptionMapper;
import com.mathweb.repository.SubscriptionRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.service.EmailService;
import com.mathweb.service.SubscriptionService;
import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.orders.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionServiceImpl.class);

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PayPalHttpClient payPalHttpClient;
    private final SubscriptionMapper subscriptionMapper;

    @Value("${paypal.subscription.amount}")
    private String amount;

    @Value("${paypal.subscription.currency}")
    private String currency;

    @Value("${app.base-url}")
    private String baseUrl;

    public SubscriptionServiceImpl(SubscriptionRepository subscriptionRepository,
                                   UserRepository userRepository,
                                   EmailService emailService,
                                   PayPalHttpClient payPalHttpClient,
                                   SubscriptionMapper subscriptionMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.payPalHttpClient = payPalHttpClient;
        this.subscriptionMapper = subscriptionMapper;
    }

    @Override
    @Transactional
    public String createSubscription(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // Check if already has active subscription
        subscriptionRepository.findByUser(user).ifPresent(sub -> {
            if (sub.isCurrentlyActive()) {
                throw new BadRequestException("You already have an active subscription");
            }
        });

        try {
            // Build PayPal order
            OrderRequest orderRequest = new OrderRequest();
            orderRequest.checkoutPaymentIntent("CAPTURE");

            ApplicationContext applicationContext = new ApplicationContext()
                    .brandName("MathWeb")
                    .landingPage("BILLING")
                    .userAction("PAY_NOW")
                    .returnUrl(baseUrl + "/api/subscriptions/capture")
                    .cancelUrl(baseUrl + "/subscription/cancel");

            orderRequest.applicationContext(applicationContext);

            PurchaseUnitRequest purchaseUnit = new PurchaseUnitRequest()
                    .description("MathWeb Monthly Subscription")
                    .amountWithBreakdown(new AmountWithBreakdown()
                            .currencyCode(currency)
                            .value(amount));

            orderRequest.purchaseUnits(List.of(purchaseUnit));

            OrdersCreateRequest request = new OrdersCreateRequest();
            request.requestBody(orderRequest);

            HttpResponse<Order> response = payPalHttpClient.execute(request);
            Order order = response.result();

            // Save pending subscription
            Subscription subscription = subscriptionRepository.findByUser(user)
                    .orElse(Subscription.builder().user(user).build());

            subscription.setStatus(SubscriptionStatus.INCOMPLETE);
            subscription.setAmountCents(249);
            subscription.setCurrency(currency.toLowerCase());
            subscriptionRepository.save(subscription);

            // Return the PayPal approval URL
            return order.links().stream()
                    .filter(link -> "approve".equals(link.rel()))
                    .findFirst()
                    .map(LinkDescription::href)
                    .orElseThrow(() -> new SubscriptionException(
                            "Could not get PayPal approval URL"));

        } catch (IOException e) {
            log.error("PayPal error creating order: {}", e.getMessage());
            throw new SubscriptionException("Failed to create PayPal order: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public SubscriptionResponse captureSubscription(String orderId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        try {
            // Capture the PayPal payment
            OrdersCaptureRequest request = new OrdersCaptureRequest(orderId);
            request.requestBody(new OrderActionRequest());

            HttpResponse<Order> response = payPalHttpClient.execute(request);
            Order order = response.result();

            if (!"COMPLETED".equals(order.status())) {
                throw new SubscriptionException("PayPal payment not completed");
            }

            // Update subscription record
            Subscription subscription = subscriptionRepository.findByUser(user)
                    .orElse(Subscription.builder().user(user).build());

            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setStripeSubscriptionId(orderId); // reusing field for PayPal order ID
            subscription.setAmountCents(249);
            subscription.setCurrency(currency.toLowerCase());
            subscription.setCurrentPeriodStart(LocalDateTime.now());
            subscription.setCurrentPeriodEnd(LocalDateTime.now().plusMonths(1));
            subscriptionRepository.save(subscription);

            // Send confirmation email
            emailService.sendSubscriptionConfirmationEmail(
                    user.getEmail(), user.getFirstName());

            log.info("Subscription activated for user {}", user.getEmail());
            return mapToResponse(subscription);

        } catch (IOException e) {
            log.error("PayPal error capturing order: {}", e.getMessage());
            throw new SubscriptionException("Failed to capture PayPal payment: " + e.getMessage());
        }
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
    public void cancelSubscription(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Subscription subscription = subscriptionRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("No subscription found"));

        if (!subscription.isCurrentlyActive()) {
            throw new BadRequestException("No active subscription to cancel");
        }

        // PayPal one-time payments don't need API cancellation
        // Just mark as cancelled in our system
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        emailService.sendSubscriptionCancelledEmail(
                user.getEmail(), user.getFirstName());

        log.info("Subscription cancelled for user {}", userId);
    }

    // ===== PRIVATE HELPERS =====

    private SubscriptionResponse mapToResponse(Subscription subscription) {
        return subscriptionMapper.toResponse(subscription);
    }
}