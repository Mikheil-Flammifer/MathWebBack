package com.mathweb.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResendOtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email is too long")
    private String email;

    @NotBlank(message = "Purpose is required")
    @Pattern(regexp = "EMAIL_VERIFICATION|PASSWORD_RESET",
            message = "Purpose must be EMAIL_VERIFICATION or PASSWORD_RESET")
    private String purpose;  // EMAIL_VERIFICATION or PASSWORD_RESET
}