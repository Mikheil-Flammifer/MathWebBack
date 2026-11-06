package com.mathweb.service.impl;

import com.mathweb.enums.NotificationType;
import com.mathweb.repository.OtpCodeRepository;
import com.mathweb.repository.RefreshTokenRepository;
import com.mathweb.repository.SubscriptionRepository;
import com.mathweb.enums.SubscriptionStatus;
import com.mathweb.service.NotificationService;
import com.mathweb.util.DateUtil;
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
    private final TokenBlacklistService tokenBlacklistService;
    private final RateLimitService rateLimitService;
    private final NotificationService notificationService;

    public ScheduledTaskService(OtpCodeRepository otpCodeRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                SubscriptionRepository subscriptionRepository,
                                TokenBlacklistService tokenBlacklistService,
                                RateLimitService rateLimitService, NotificationService notificationService) {
        this.otpCodeRepository = otpCodeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tokenBlacklistService = tokenBlacklistService;
        this.rateLimitService = rateLimitService;
        this.notificationService = notificationService;
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
            notificationService.notifyOnce(sub.getUser().getId(),
                    NotificationType.SUBSCRIPTION_EXPIRED,
                    "Your access has expired",
                    "Renew your plan to keep watching videos and solving quests.",
                    "/subscription",
                    "SUB_EXPIRED:" + sub.getCurrentPeriodEnd().toLocalDate());
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

    @Scheduled(fixedRate = 900000) // every 15 minutes
    public void cleanupRateLimitEntries() {
        rateLimitService.cleanupExpiredEntries();
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanupBlacklistedTokens() {
        log.info("Cleaning up blacklisted tokens...");
        tokenBlacklistService.removeExpiredTokens();
    }

    // Daily at 09:00: remind users whose access ends within 3 days
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void notifyExpiringSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        subscriptionRepository.findActiveEndingBetween(now, now.plusDays(3)).forEach(sub ->
                notificationService.notifyOnce(sub.getUser().getId(),
                        NotificationType.SUBSCRIPTION_EXPIRING,
                        "Your access ends soon",
                        "Your plan ends on " + DateUtil.formatDate(sub.getCurrentPeriodEnd()) + ".",
                        "/subscription",
                        "SUB_EXPIRING:" + sub.getCurrentPeriodEnd().toLocalDate()));
    }

    // Daily at 03:30: delete notifications older than 90 days
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOldNotifications() {
        int n = notificationService.purgeOlderThan(90);
        log.info("Deleted {} old notifications", n);
    }
}