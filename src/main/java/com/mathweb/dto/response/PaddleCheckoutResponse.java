package com.mathweb.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PaddleCheckoutResponse(
        UUID orderId,
        String transactionId,
        String checkoutUrl,
        Instant expiresAt
) {
}