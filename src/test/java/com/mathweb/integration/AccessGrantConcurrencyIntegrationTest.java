package com.mathweb.integration;

import com.mathweb.entity.Plan;
import com.mathweb.entity.Subscription;
import com.mathweb.entity.User;
import com.mathweb.enums.Role;
import com.mathweb.repository.PlanRepository;
import com.mathweb.repository.SubscriptionRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.service.impl.AccessGrantService;
import com.mathweb.service.impl.AccessGrantService.GrantResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// Deliberately NOT @Transactional: each thread must commit for real.
class AccessGrantConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired AccessGrantService accessGrantService;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void twoSimultaneousDifferentPurchases_bothSucceed_andExtendAccessTwice()
            throws Exception {

        User user = createUser();
        Plan pass30 = planRepository.findByCodeAndActiveTrue("PASS_30").orElseThrow();
        Plan pass90 = planRepository.findByCodeAndActiveTrue("PASS_90").orElseThrow();

        String txnA = "txn_" + UUID.randomUUID();
        String txnB = "txn_" + UUID.randomUUID();

        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Callable<GrantResult> purchaseA = () -> {
                startTogether.await(10, TimeUnit.SECONDS);
                return accessGrantService.grantAccess(
                        user.getId(), pass30.getId(), "PADDLE", txnA,
                        pass30.getPriceCents(), "USD");
            };
            Callable<GrantResult> purchaseB = () -> {
                startTogether.await(10, TimeUnit.SECONDS);
                return accessGrantService.grantAccess(
                        user.getId(), pass90.getId(), "PADDLE", txnB,
                        pass90.getPriceCents(), "USD");
            };

            Future<GrantResult> a = pool.submit(purchaseA);
            Future<GrantResult> b = pool.submit(purchaseB);

            // get() rethrows any exception from the worker thread
            assertThat(a.get(30, TimeUnit.SECONDS)).isEqualTo(GrantResult.GRANTED);
            assertThat(b.get(30, TimeUnit.SECONDS)).isEqualTo(GrantResult.GRANTED);
        } finally {
            pool.shutdownNow();
        }

        Integer payments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE user_id = ?",
                Integer.class, user.getId());
        assertThat(payments).isEqualTo(2);

        Subscription sub = subscriptionRepository.findByUser(user).orElseThrow();

        // 30 + 90 days, regardless of which thread won the lock.
        // Millisecond tolerance covers Postgres microsecond rounding.
        assertThat(sub.getCurrentPeriodEnd()).isCloseTo(
                sub.getCurrentPeriodStart().plusDays(120),
                within(1, ChronoUnit.MILLIS));
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