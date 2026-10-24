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
    @Size(max = 5000, message = "Question text is too long (max 5000 characters)")
    private String questionText;

    @NotNull(message = "Problem type is required")
    private ProblemType problemType;

    @NotNull(message = "Difficulty level is required")
    private DifficultyLevel difficultyLevel;

    @Size(max = 500, message = "Correct answer is too long (max 500 characters)")
    private String correctAnswer;          // OPEN_ANSWER / NUMERIC

    @Size(max = 5000, message = "Explanation is too long (max 5000 characters)")
    private String explanation;

    @NotNull(message = "Order index is required")
    @Min(value = 0, message = "Order index cannot be negative")
    private Integer orderIndex = 0;

    @NotNull(message = "XP reward is required")
    @Min(value = 0, message = "XP reward cannot be negative")
    @Max(value = 1000, message = "XP reward cannot be more than 1000")
    private Integer xpReward = 10;

    private Long categoryId;

    @Size(max = 500, message = "Graph link is too long")
    private String embedUrl;

    @NotNull(message = "Answer options list is required (can be empty)")
    @Size(max = 10, message = "A problem can have at most 10 answer options")
    @Valid
    private List<CreateProblemRequest.AnswerOptionRequest> answerOptions = new ArrayList<>();

    @DecimalMin(value = "0", message = "Tolerance cannot be negative")
    private Double answerTolerance;        // NUMERIC only

    @Size(max = 20, message = "A problem can have at most 20 hints")
    private List<@Size(max = 1000, message = "A hint is too long (max 1000 characters)") String> hints = new ArrayList<>();
}