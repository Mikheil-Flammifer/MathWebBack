package com.mathweb.dto.request;

import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.ProblemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateProblemRequest {

    @NotNull(message = "Quest ID is required")
    private Long questId;

    @NotBlank(message = "Question text is required")
    private String questionText;

    @NotNull(message = "Problem type is required")
    private ProblemType problemType;

    @NotNull(message = "Difficulty level is required")
    private DifficultyLevel difficultyLevel;

    // For OPEN_ANSWER only
    private String correctAnswer;

    private String explanation;
    private Integer orderIndex = 0;
    private Integer xpReward = 10;

    // For MULTIPLE_CHOICE only
    private List<AnswerOptionRequest> answerOptions = new ArrayList<>();

    @Data
    public static class AnswerOptionRequest {
        @NotBlank(message = "Option text is required")
        private String optionText;
        private Boolean isCorrect = false;
        private Integer orderIndex = 0;
    }
}