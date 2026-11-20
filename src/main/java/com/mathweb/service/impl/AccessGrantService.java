package com.mathweb.service.impl;

import com.mathweb.entity.Payment;
import com.mathweb.entity.Plan;
import com.mathweb.entity.Subscription;
import com.mathweb.entity.User;
import com.mathweb.enums.SubscriptionStatus;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.PaymentIdentityConflictException;
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

    public enum GrantResult { GRANTED, ALREADY_PROCESSED }

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
     * Call this ONLY after the payment was validated against its checkout order.
     *
     * @return GRANTED, or ALREADY_PROCESSED if an identical payment exists
     * @throws PaymentIdentityConflictException if the transaction ID exists
     *         with different provider, user, plan, currency or amount
     */
    @Transactional
    public GrantResult grantAccess(Long userId, Long planId, String provider,
                                   String transactionId, int amountCents, String currency) {

        String cur = currency.toUpperCase(Locale.ROOT);

        // Serializes all access changes for this user, even if no subscription row exists yet.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));

        LocalDateTime now = LocalDateTime.now();

        int inserted = paymentRepository.insertIfAbsent(
                userId, planId, provider, transactionId, amountCents, cur, now);

        if (inserted == 0) {
            Payment existing = paymentRepository.findByProviderTransactionId(transactionId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Payment vanished after conflict: " + transactionId));

            boolean same = existing.getProvider().equals(provider)
                    && existing.getUser().getId().equals(userId)
                    && existing.getPlan().getId().equals(planId)
                    && existing.getCurrency().equalsIgnoreCase(cur)
                    && existing.getAmountCents() == amountCents;

            if (!same) {
                throw new PaymentIdentityConflictException(
                        "Transaction " + transactionId
                                + " already exists with different details");
            }

            log.info("Payment {} already processed, ignoring", transactionId);
            return GrantResult.ALREADY_PROCESSED;
        }

        Subscription sub = subscriptionRepository.findByUser(user)
                .orElseGet(() -> Subscription.builder().user(user).build());

        extend(sub, plan.getDurationDays(), now);
        sub.setAmountCents(amountCents);
        sub.setCurrency(cur);
        subscriptionRepository.save(sub);

        log.info("Access granted: user {} plan {} until {}",
                userId, plan.getCode(), sub.getCurrentPeriodEnd());
        return GrantResult.GRANTED;
    }

    /**
     * Admin gift: adds days without a payment. Returns the saved subscription.
     * The caller is responsible for writing the audit row.
     */
    @Transactional
    public Subscription grantFreeDays(Long userId, int days) {
        if (days <= 0) {
            throw new BadRequestException("Days must be at least 1");
        }
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Subscription sub = subscriptionRepository.findByUser(user)
                .orElseGet(() -> Subscription.builder().user(user).build());

        extend(sub, days, LocalDateTime.now());
        if (sub.getAmountCents() == null) {
            sub.setAmountCents(0);   // never overwrite what a real payment recorded
        }
        sub = subscriptionRepository.save(sub);

        log.info("Free access granted: user {} +{} days, until {}",
                userId, days, sub.getCurrentPeriodEnd());
        return sub;
    }

    /** Ends running access now. Returns the saved subscription. */
    @Transactional
    public Subscription revoke(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Subscription sub = subscriptionRepository.findByUser(user)
                .orElseThrow(() -> new BadRequestException("This user has no active access"));
        if (!sub.isCurrentlyActive()) {
            throw new BadRequestException("This user has no active access");
        }

        LocalDateTime now = LocalDateTime.now();
        sub.setStatus(SubscriptionStatus.CANCELLED);
        sub.setCancelledAt(now);
        sub.setCurrentPeriodEnd(now);
        sub = subscriptionRepository.save(sub);

        log.info("Access revoked: user {}", userId);
        return sub;
    }

    // Still active: add days after the current end, so early renewals lose nothing.
    // Expired or new: start from now.
    private void extend(Subscription sub, int days, LocalDateTime now) {
        LocalDateTime base = now;
        if (sub.isCurrentlyActive()) {
            base = sub.getCurrentPeriodEnd();
        } else {
            sub.setCurrentPeriodStart(now);
        }
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCurrentPeriodEnd(base.plusDays(days));
        sub.setCancelledAt(null);
    }
}