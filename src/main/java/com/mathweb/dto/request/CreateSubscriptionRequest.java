package com.mathweb.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSubscriptionRequest {

    // Stripe payment method ID from the frontend
    @NotBlank(message = "Payment method ID is required")
    private String paymentMethodId;
}