package com.mathweb.service.impl;

import com.mathweb.repository.OtpCodeRepository;
import com.mathweb.repository.RefreshTokenRepository;
import com.mathweb.repository.SubscriptionRepository;
import com.mathweb.enums.SubscriptionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScheduledTaskService {

    private static final Logger log =
            LoggerFactory.getLogger(ScheduledTaskService.class);

    private final OtpCodeRepository otpCodeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SubscriptionRepository subscriptionRepository;

    public ScheduledTaskService(OtpCodeRepository otpCodeRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                SubscriptionRepository subscriptionRepository) {
        this.otpCodeRepository = otpCodeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    // ===== RUNS EVERY HOUR =====

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredOtps() {
        log.info("Running OTP cleanup task...");
        otpCodeRepository.deleteExpiredCodes(LocalDateTime.now());
        log.info("OTP cleanup completed");
    }

    // ===== RUNS EVERY HOUR =====

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredRefreshTokens() {
        log.info("Running refresh token cleanup task...");
        List<com.mathweb.entity.RefreshToken> expired =
                refreshTokenRepository.findAll().stream()
                        .filter(t -> !t.isValid())
                        .toList();
        refreshTokenRepository.deleteAll(expired);
        log.info("Deleted {} expired refresh tokens", expired.size());
    }

    // ===== RUNS EVERY DAY AT MIDNIGHT =====

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void checkExpiredSubscriptions() {
        log.info("Running subscription expiry check...");
        List<com.mathweb.entity.Subscription> expired =
                subscriptionRepository.findExpiredActiveSubscriptions(
                        LocalDateTime.now());

        expired.forEach(sub -> {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);
            log.info("Marked subscription as expired for user: {}",
                    sub.getUser().getEmail());
        });

        log.info("Subscription check completed. {} subscriptions expired",
                expired.size());
    }

    // ===== RUNS EVERY DAY AT 2AM =====

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupIncompleteSubscriptions() {
        log.info("Running incomplete subscription cleanup...");
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);

        List<com.mathweb.entity.Subscription> incomplete =
                subscriptionRepository.findByStatus(SubscriptionStatus.INCOMPLETE)
                        .stream()
                        .filter(s -> s.getCreatedAt().isBefore(cutoff))
                        .toList();

        subscriptionRepository.deleteAll(incomplete);
        log.info("Deleted {} incomplete subscriptions", incomplete.size());
    }
}