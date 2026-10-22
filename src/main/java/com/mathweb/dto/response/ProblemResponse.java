package com.mathweb.dto.response;

import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.ProblemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemResponse {

    private Long id;
    private String questionText;
    private String questionImagePath;
    private ProblemType problemType;
    private DifficultyLevel difficultyLevel;
    private Integer orderIndex;
    private Integer xpReward;
    private Long questId;
    private Long categoryId;
    private String categoryName;
    private Long mainCategoryId;
    private String mainCategoryName;
    private LocalDateTime retryAvailableAt;
    private Integer hintCount;      // everyone: how many hints exist
    private Integer hintsUsed;
    private List<String> hints;     // staff only (students get revealed hints in the next checkpoint)

    // Answer options for MULTIPLE_CHOICE (isCorrect hidden from student)
    private List<AnswerOptionResponse> answerOptions;

    private Double answerTolerance;   // staff only

    // Only shown after attempts exhausted
    private String explanation;
    private String explanationImagePath;
    private String correctAnswer;

    // Per-user state
    private Integer attemptsUsed;
    private Boolean solved;
    private Boolean answerRevealed;
}