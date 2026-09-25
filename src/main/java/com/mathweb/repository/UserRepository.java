package com.mathweb.repository;

import com.mathweb.entity.User;
import com.mathweb.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);

    List<User> findByActiveTrue();

    @Query("SELECT u FROM User u WHERE u.emailVerified = false AND u.active = true")
    List<User> findUnverifiedUsers();

    @Query("SELECT u FROM User u WHERE u.stripeCustomerId = :stripeCustomerId")
    Optional<User> findByStripeCustomerId(String stripeCustomerId);
}