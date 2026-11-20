package com.mathweb.controller;

import com.mathweb.dto.request.CreatePaddleCheckoutRequest;
import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.PaddleCheckoutResponse;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.impl.PaddleCheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/paddle")
public class PaddleCheckoutController {

    private final PaddleCheckoutService checkoutService;

    public PaddleCheckoutController(
            PaddleCheckoutService checkoutService
    ) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<PaddleCheckoutResponse>>
    createCheckout(
            @Valid @RequestBody
            CreatePaddleCheckoutRequest request,

            @AuthenticationPrincipal
            UserPrincipal principal
    ) {
        PaddleCheckoutResponse checkout =
                checkoutService.createCheckout(
                        principal.getId(),
                        request.planCode()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Checkout created",
                        checkout
                ));
    }
}