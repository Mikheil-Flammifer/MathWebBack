package com.mathweb.dto.request;

import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.ProblemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UpdateProblemRequest {

    @NotBlank(message = "Question text is required")
    private String questionText;

    @NotNull(message = "Problem type is required")
    private ProblemType problemType;

    @NotNull(message = "Difficulty level is required")
    private DifficultyLevel difficultyLevel;

    private String correctAnswer;          // OPEN_ANSWER
    private String explanation;
    private Integer orderIndex = 0;
    private Integer xpReward = 10;

    @Min(1) @Max(10)
    private Integer maxAttempts = 3;

    private Long categoryId;

    @Valid
    private List<CreateProblemRequest.AnswerOptionRequest> answerOptions = new ArrayList<>();
}