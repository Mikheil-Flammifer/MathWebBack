package com.mathweb.repository;

import com.mathweb.entity.Subscription;
import com.mathweb.entity.User;
import com.mathweb.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUser(User user);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    List<Subscription> findByStatus(SubscriptionStatus status);

    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.currentPeriodEnd < :now")
    List<Subscription> findExpiredActiveSubscriptions(LocalDateTime now);

    boolean existsByUserAndStatus(User user, SubscriptionStatus status);
}