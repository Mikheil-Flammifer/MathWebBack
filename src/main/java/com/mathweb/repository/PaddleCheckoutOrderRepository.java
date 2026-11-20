package com.mathweb.repository;

import com.mathweb.entity.PaddleCheckoutOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaddleCheckoutOrderRepository
        extends JpaRepository<PaddleCheckoutOrder, UUID> {

    Optional<PaddleCheckoutOrder> findByPaddleTransactionId(
            String paddleTransactionId
    );
}