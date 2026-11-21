package com.mathweb.integration;

import com.mathweb.entity.Plan;
import com.mathweb.entity.Subscription;
import com.mathweb.entity.User;
import com.mathweb.enums.Role;
import com.mathweb.exception.PaymentIdentityConflictException;
import com.mathweb.repository.PlanRepository;
import com.mathweb.repository.SubscriptionRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.service.impl.AccessGrantService;
import com.mathweb.service.impl.AccessGrantService.GrantResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

// Deliberately NOT @Transactional: each thread must commit for real.
class AccessGrantConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired AccessGrantService accessGrantService;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired JdbcTemplate jdbc;

    // ---------------------------------------------------------------
    // 1. Two simultaneous different purchases for one user
    // ---------------------------------------------------------------
    @Test
    void twoSimultaneousDifferentPurchases_bothSucceed_andExtendAccessTwice()
            throws Exception {

        User user = createUser();
        Plan pass30 = plan("PASS_30");
        Plan pass90 = plan("PASS_90");

        String txnA = newTxn();
        String txnB = newTxn();

        List<GrantResult> results = runConcurrently(List.of(
                () -> accessGrantService.grantAccess(user.getId(), pass30.getId(),
                        "PADDLE", txnA, pass30.getPriceCents(), "USD"),
                () -> accessGrantService.grantAccess(user.getId(), pass90.getId(),
                        "PADDLE", txnB, pass90.getPriceCents(), "USD")));

        assertThat(results).containsOnly(GrantResult.GRANTED);
        assertThat(paymentCountForUser(user)).isEqualTo(2);

        Subscription sub = subscriptionRepository.findByUser(user).orElseThrow();
        // 30 + 90 days, regardless of which thread won the lock
        assertEndIsStartPlusDays(sub, 120);
    }

    // ---------------------------------------------------------------
    // 2. Two simultaneous deliveries of the same transaction
    // ---------------------------------------------------------------
    @Test
    void twoSimultaneousDeliveriesOfSameTransaction_createOnePayment_andOneExtension()
            throws Exception {

        User user = createUser();
        Plan pass30 = plan("PASS_30");
        String txn = newTxn();

        Callable<GrantResult> delivery = () -> accessGrantService.grantAccess(
                user.getId(), pass30.getId(), "PADDLE", txn,
                pass30.getPriceCents(), "USD");

        List<GrantResult> results = runConcurrently(List.of(delivery, delivery));

        assertThat(results).containsExactlyInAnyOrder(
                GrantResult.GRANTED, GrantResult.ALREADY_PROCESSED);

        Integer payments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE provider_transaction_id = ?",
                Integer.class, txn);
        assertThat(payments).isEqualTo(1);

        Subscription sub = subscriptionRepository.findByUser(user).orElseThrow();
        assertEndIsStartPlusDays(sub, 30); // one extension, not 60
    }

    // ---------------------------------------------------------------
    // 3. Duplicate transaction with different data is rejected
    // ---------------------------------------------------------------
    @Test
    void duplicateTransactionWithDifferentAmount_throwsIdentityConflict_andGrantsNothingMore() {
        User user = createUser();
        Plan pass30 = plan("PASS_30");
        String txn = newTxn();

        accessGrantService.grantAccess(user.getId(), pass30.getId(), "PADDLE",
                txn, pass30.getPriceCents(), "USD");
        LocalDateTime endBefore = periodEnd(user);

        assertThatThrownBy(() -> accessGrantService.grantAccess(
                user.getId(), pass30.getId(), "PADDLE", txn,
                pass30.getPriceCents() + 1, "USD"))
                .isInstanceOf(PaymentIdentityConflictException.class);

        assertThat(paymentCountForTxn(txn)).isEqualTo(1);
        assertThat(periodEnd(user)).isEqualTo(endBefore);
    }

    @Test
    void duplicateTransactionWithDifferentPlan_throwsIdentityConflict_andGrantsNothingMore() {
        User user = createUser();
        Plan pass30 = plan("PASS_30");
        Plan pass90 = plan("PASS_90");
        String txn = newTxn();

        accessGrantService.grantAccess(user.getId(), pass30.getId(), "PADDLE",
                txn, pass30.getPriceCents(), "USD");
        LocalDateTime endBefore = periodEnd(user);

        assertThatThrownBy(() -> accessGrantService.grantAccess(
                user.getId(), pass90.getId(), "PADDLE", txn,
                pass30.getPriceCents(), "USD"))
                .isInstanceOf(PaymentIdentityConflictException.class);

        assertThat(paymentCountForTxn(txn)).isEqualTo(1);
        assertThat(periodEnd(user)).isEqualTo(endBefore);
    }

    @Test
    void duplicateTransactionForDifferentUser_throwsIdentityConflict_andGrantsNothingToThem() {
        User owner = createUser();
        User attacker = createUser();
        Plan pass30 = plan("PASS_30");
        String txn = newTxn();

        accessGrantService.grantAccess(owner.getId(), pass30.getId(), "PADDLE",
                txn, pass30.getPriceCents(), "USD");

        assertThatThrownBy(() -> accessGrantService.grantAccess(
                attacker.getId(), pass30.getId(), "PADDLE", txn,
                pass30.getPriceCents(), "USD"))
                .isInstanceOf(PaymentIdentityConflictException.class);

        assertThat(paymentCountForTxn(txn)).isEqualTo(1);
        assertThat(subscriptionRepository.findByUser(attacker)).isEmpty();
    }

    // ---------------------------------------------------------------
    // 4. Subscription creation is safe when none exists yet
    // ---------------------------------------------------------------
    @Test
    void firstPurchasesRacingForNewUser_createExactlyOneSubscription()
            throws Exception {

        User user = createUser(); // no subscription row yet
        Plan pass30 = plan("PASS_30");

        assertThat(subscriptionCountForUser(user)).isZero();

        String txnA = newTxn();
        String txnB = newTxn();
        String txnC = newTxn();

        List<GrantResult> results = runConcurrently(List.of(
                () -> accessGrantService.grantAccess(user.getId(), pass30.getId(),
                        "PADDLE", txnA, pass30.getPriceCents(), "USD"),
                () -> accessGrantService.grantAccess(user.getId(), pass30.getId(),
                        "PADDLE", txnB, pass30.getPriceCents(), "USD"),
                () -> accessGrantService.grantAccess(user.getId(), pass30.getId(),
                        "PADDLE", txnC, pass30.getPriceCents(), "USD")));

        assertThat(results).containsOnly(GrantResult.GRANTED);
        assertThat(subscriptionCountForUser(user)).isEqualTo(1);
        assertThat(paymentCountForUser(user)).isEqualTo(3);

        Subscription sub = subscriptionRepository.findByUser(user).orElseThrow();
        assertEndIsStartPlusDays(sub, 90);
    }

    // ---------------------------------------------------------------
    // 5. Paid access and admin free days at the same time
    // ---------------------------------------------------------------
    @Test
    void paidAccessAndFreeDaysRacing_bothApplied() throws Exception {
        User user = createUser();
        Plan pass30 = plan("PASS_30");
        String txn = newTxn();

        runConcurrently(List.of(
                () -> accessGrantService.grantAccess(user.getId(), pass30.getId(),
                        "PADDLE", txn, pass30.getPriceCents(), "USD"),
                () -> {
                    accessGrantService.grantFreeDays(user.getId(), 7);
                    return GrantResult.GRANTED;
                }));

        Subscription sub = subscriptionRepository.findByUser(user).orElseThrow();
        assertEndIsStartPlusDays(sub, 37);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Starts all callables at the same instant and returns their results. */
    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        CyclicBarrier startTogether = new CyclicBarrier(tasks.size());
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                // get() rethrows worker exceptions, so any leaked error fails the test
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertEndIsStartPlusDays(Subscription sub, int days) {
        assertThat(sub.getCurrentPeriodEnd()).isCloseTo(
                sub.getCurrentPeriodStart().plusDays(days),
                within(1, ChronoUnit.MILLIS));
    }

    private LocalDateTime periodEnd(User user) {
        return subscriptionRepository.findByUser(user).orElseThrow().getCurrentPeriodEnd();
    }

    private int paymentCountForUser(User user) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE user_id = ?",
                Integer.class, user.getId());
    }

    private int paymentCountForTxn(String txn) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE provider_transaction_id = ?",
                Integer.class, txn);
    }

    private int subscriptionCountForUser(User user) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscriptions WHERE user_id = ?",
                Integer.class, user.getId());
    }

    private Plan plan(String code) {
        return planRepository.findByCodeAndActiveTrue(code).orElseThrow();
    }

    private static String newTxn() {
        return "txn_" + UUID.randomUUID();
    }

    private User createUser() {
        return userRepository.saveAndFlush(
                User.builder()
                        .email("it-" + UUID.randomUUID() + "@example.test")
                        .firstName("Test")
                        .lastName("User")
                        .passwordHash("not-used")
                        .role(Role.STUDENT)   // use your real enum and value
                        .build());
    }
}