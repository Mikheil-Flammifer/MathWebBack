package com.mathweb.service.impl;

import com.mathweb.dto.response.PaddleCheckoutResponse;
import com.mathweb.entity.PaddleCheckoutOrder;
import com.mathweb.entity.Plan;
import com.mathweb.entity.User;
import com.mathweb.enums.PaddleCheckoutOrderStatus;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.PaddleCheckoutOrderRepository;
import com.mathweb.repository.PlanRepository;
import com.mathweb.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaddleCheckoutService {

    private static final Logger log =
            LoggerFactory.getLogger(PaddleCheckoutService.class);

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final PaddleCheckoutOrderRepository orderRepository;
    private final PaddleApiClient paddleApiClient;
    private final TransactionTemplate transactionTemplate;
    private final Duration orderTtl;

    public PaddleCheckoutService(
            UserRepository userRepository,
            PlanRepository planRepository,
            PaddleCheckoutOrderRepository orderRepository,
            PaddleApiClient paddleApiClient,
            PlatformTransactionManager transactionManager,
            @Value("${paddle.checkout-order-ttl:PT24H}")
            Duration orderTtl
    ) {
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.orderRepository = orderRepository;
        this.paddleApiClient = paddleApiClient;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
        this.orderTtl = orderTtl;

        if (orderTtl.isNegative() || orderTtl.isZero()) {
            throw new IllegalStateException(
                    "Paddle checkout-order TTL must be positive"
            );
        }
    }

    public PaddleCheckoutResponse createCheckout(
            Long authenticatedUserId,
            String requestedPlanCode
    ) {
        PendingOrder pending = transactionTemplate.execute(
                status -> createPendingOrder(
                        authenticatedUserId,
                        requestedPlanCode
                )
        );

        if (pending == null) {
            throw new IllegalStateException(
                    "Failed to create pending checkout order"
            );
        }

        PaddleApiClient.CreatedTransaction transaction;

        try {
            transaction =
                    paddleApiClient.createCheckoutTransaction(
                            pending.orderId(),
                            pending.priceId()
                    );
        } catch (RuntimeException exception) {
            markFailedSafely(
                    pending.orderId(),
                    "Paddle transaction creation failed"
            );
            throw exception;
        }

        /*
         * This transaction stores the Paddle transaction ID before the
         * checkout URL is returned to the frontend.
         */
        transactionTemplate.executeWithoutResult(
                status -> markReady(
                        pending.orderId(),
                        transaction.transactionId(),
                        transaction.checkoutUrl()
                )
        );

        return new PaddleCheckoutResponse(
                pending.orderId(),
                transaction.transactionId(),
                transaction.checkoutUrl(),
                pending.expiresAt()
        );
    }

    private PendingOrder createPendingOrder(
            Long userId,
            String requestedPlanCode
    ) {
        String planCode = requestedPlanCode
                .trim()
                .toUpperCase(Locale.ROOT);

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User", userId));

        Plan plan = planRepository
                .findByCodeAndActiveTrue(planCode)
                .orElseThrow(() ->
                        new BadRequestException(
                                "The selected plan is not available"
                        ));

        if (plan.getPaddlePriceId() == null
                || plan.getPaddlePriceId().isBlank()) {
            throw new BadRequestException(
                    "The selected plan is not available for checkout"
            );
        }

        if (plan.getPriceCents() <= 0
                || plan.getCurrency() == null
                || plan.getCurrency().isBlank()) {
            throw new IllegalStateException(
                    "Plan has invalid payment configuration: "
                            + plan.getCode()
            );
        }

        Instant now = Instant.now();
        UUID orderId = UUID.randomUUID();

        PaddleCheckoutOrder order =
                PaddleCheckoutOrder.builder()
                        .id(orderId)
                        .user(user)
                        .plan(plan)
                        .expectedPriceId(
                                plan.getPaddlePriceId()
                        )
                        .expectedSubtotalCents(
                                (long) plan.getPriceCents()
                        )
                        .expectedCurrency(
                                plan.getCurrency()
                                        .toUpperCase(Locale.ROOT)
                        )
                        .status(
                                PaddleCheckoutOrderStatus.PENDING
                        )
                        .expiresAt(now.plus(orderTtl))
                        .createdAt(now)
                        .build();

        orderRepository.saveAndFlush(order);

        return new PendingOrder(
                orderId,
                plan.getPaddlePriceId(),
                order.getExpiresAt()
        );
    }

    private void markReady(
            UUID orderId,
            String transactionId,
            String checkoutUrl
    ) {
        PaddleCheckoutOrder order = orderRepository
                .findById(orderId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Checkout order disappeared: " + orderId
                        ));

        if (order.getStatus()
                != PaddleCheckoutOrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Checkout order is not pending: " + orderId
            );
        }

        order.setPaddleTransactionId(transactionId);
        order.setCheckoutUrl(checkoutUrl);
        order.setStatus(PaddleCheckoutOrderStatus.READY);
        order.setFailureReason(null);

        orderRepository.saveAndFlush(order);
    }

    private void markFailedSafely(
            UUID orderId,
            String reason
    ) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                orderRepository.findById(orderId)
                        .filter(order ->
                                order.getStatus()
                                        == PaddleCheckoutOrderStatus.PENDING)
                        .ifPresent(order -> {
                            order.setStatus(
                                    PaddleCheckoutOrderStatus.FAILED
                            );
                            order.setFailureReason(reason);
                            orderRepository.saveAndFlush(order);
                        });
            });
        } catch (RuntimeException failureRecordingException) {
            log.error(
                    "Could not mark checkout order {} as failed",
                    orderId,
                    failureRecordingException
            );
        }
    }

    private record PendingOrder(
            UUID orderId,
            String priceId,
            Instant expiresAt
    ) {
    }
}