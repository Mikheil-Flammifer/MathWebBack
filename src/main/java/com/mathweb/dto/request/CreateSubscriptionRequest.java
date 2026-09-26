package com.mathweb.dto.request;

import lombok.Data;

@Data
public class CreateSubscriptionRequest {
    // orderId returned by PayPal after user approves payment
    private String orderId;
}