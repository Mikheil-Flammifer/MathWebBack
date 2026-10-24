package com.mathweb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSubscriptionRequest {
    // orderId returned by PayPal after user approves payment
    @NotBlank(message = "Order ID is required")
    @Size(max = 64, message = "Order ID is too long")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Order ID is not valid")
    private String orderId;
}