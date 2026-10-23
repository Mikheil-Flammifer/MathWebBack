package com.mathweb.dto.request;

import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.ProblemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    private String correctAnswer;          // OPEN_ANSWER / NUMERIC
    private String explanation;
    private Integer orderIndex = 0;
    private Integer xpReward = 10;

    private Long categoryId;

    private String embedUrl;

    @Valid
    private List<CreateProblemRequest.AnswerOptionRequest> answerOptions = new ArrayList<>();

    @DecimalMin(value = "0", message = "Tolerance cannot be negative")
    private Double answerTolerance;        // NUMERIC only

    private List<String> hints = new ArrayList<>();
}