package com.mathweb.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitAnswerRequest {

    @NotNull(message = "Problem ID is required")
    private Long problemId;

    // For MULTIPLE_CHOICE: the selected answer option ID
    private Long selectedOptionId;

    // For OPEN_ANSWER: the typed answer
    private String openAnswer;
}