package com.mathweb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePaddleCheckoutRequest(

        @NotBlank(message = "Plan code is required")
        @Size(max = 30, message = "Plan code is too long")
        String planCode
) {
}