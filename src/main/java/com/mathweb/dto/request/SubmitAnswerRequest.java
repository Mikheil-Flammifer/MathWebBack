package com.mathweb.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitAnswerRequest {

    @NotNull(message = "Problem ID is required")
    private Long problemId;

    // For MULTIPLE_CHOICE: the selected answer option ID
    private Long selectedOptionId;

    // For OPEN_ANSWER / NUMERIC: the typed answer
    @Size(max = 200, message = "Answer is too long (max 200 characters)")
    private String openAnswer;
}