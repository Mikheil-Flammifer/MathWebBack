package com.mathweb.service;

import com.mathweb.entity.Payment;
import com.mathweb.entity.Plan;
import com.mathweb.entity.Subscription;
import com.mathweb.entity.User;
import com.mathweb.enums.PaymentStatus;
import com.mathweb.enums.SubscriptionStatus;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.PaymentRepository;
import com.mathweb.repository.PlanRepository;
import com.mathweb.repository.SubscriptionRepository;
import com.mathweb.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AccessGrantService {

    private static final Logger log = LoggerFactory.getLogger(AccessGrantService.class);

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;

    public AccessGrantService(UserRepository userRepository,
                              PlanRepository planRepository,
                              PaymentRepository paymentRepository,
                              SubscriptionRepository subscriptionRepository) {
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Records a verified payment and extends the user's access.
     * Call this ONLY after the provider's signature and payment status were verified.
     *
     * @return true if access was granted, false if this transaction was already processed
     */
    @Transactional
    public boolean grantAccess(Long userId, Long planId, String provider,
                               String transactionId, int amountCents, String currency) {

        if (paymentRepository.existsByProviderTransactionId(transactionId)) {
            log.info("Payment {} already processed, ignoring", transactionId);
            return false;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));

        LocalDateTime now = LocalDateTime.now();

        // Unique constraint on provider_transaction_id is the final guard against double processing
        paymentRepository.saveAndFlush(Payment.builder()
                .user(user)
                .plan(plan)
                .provider(provider)
                .providerTransactionId(transactionId)
                .amountCents(amountCents)
                .currency(currency.toUpperCase(Locale.ROOT))
                .status(PaymentStatus.COMPLETED)
                .paidAt(now)
                .build());

        Subscription sub = subscriptionRepository.findByUser(user)
                .orElseGet(() -> Subscription.builder().user(user).build());

        // Still active: add days after the current end, so early renewals lose nothing.
        // Expired or new: start from now.
        LocalDateTime base = now;
        if (sub.isCurrentlyActive()) {
            base = sub.getCurrentPeriodEnd();
        } else {
            sub.setCurrentPeriodStart(now);
        }

        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCurrentPeriodEnd(base.plusDays(plan.getDurationDays()));
        sub.setAmountCents(amountCents);
        sub.setCurrency(currency.toUpperCase(Locale.ROOT));
        sub.setCancelledAt(null);
        subscriptionRepository.save(sub);

        log.info("Access granted: user {} plan {} until {}", userId, plan.getCode(), sub.getCurrentPeriodEnd());
        return true;
    }
}