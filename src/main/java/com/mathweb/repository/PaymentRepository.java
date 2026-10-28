package com.mathweb.repository;

import com.mathweb.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByProviderTransactionId(String providerTransactionId);
}