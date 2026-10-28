package com.mathweb.repository;

import com.mathweb.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    List<Plan> findByActiveTrueOrderByDurationDaysAsc();

    Optional<Plan> findByPaddlePriceIdAndActiveTrue(String paddlePriceId);
}