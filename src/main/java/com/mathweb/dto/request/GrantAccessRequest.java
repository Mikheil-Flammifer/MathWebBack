package com.mathweb.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GrantAccessRequest {

    @NotNull(message = "Days is required")
    @Min(value = 1, message = "Days must be at least 1")
    @Max(value = 3650, message = "Days cannot be more than 3650")
    private Integer days;

    @Size(max = 500, message = "Note is too long (max 500 characters)")
    private String note;
}