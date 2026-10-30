package com.mathweb.controller;

import com.mathweb.dto.response.ApiResponse;
import com.mathweb.repository.PlanRepository;
// import ApiResponse here: use the same import as SubscriptionController
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanRepository planRepository;

    public record PlanResponse(String code,
                               String name,
                               Integer durationDays,
                               Integer priceCents,
                               String currency,
                               String paddlePriceId) {}

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlanResponse>>> list() {
        List<PlanResponse> plans = planRepository.findByActiveTrueOrderByDurationDaysAsc().stream()
                .map(p -> new PlanResponse(
                        p.getCode(),
                        p.getName(),
                        p.getDurationDays(),
                        p.getPriceCents(),
                        p.getCurrency(),
                        p.getPaddlePriceId()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Plans retrieved", plans));
    }
}