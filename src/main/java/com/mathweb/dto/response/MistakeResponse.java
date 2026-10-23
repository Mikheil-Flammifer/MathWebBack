package com.mathweb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MistakeResponse {
    private Long problemId;
    private Long questId;
    private String questionText;
    private Long categoryId;
    private String categoryName;
    private Long mainCategoryId;
    private String mainCategoryName;
    private String reason;          // GAVE_UP, UNSOLVED_WRONG, SOLVED_WITH_HINTS, SOLVED_AFTER_RETRIES
    private Boolean solved;
    private Integer attemptsUsed;
    private Integer hintsUsed;
    private LocalDateTime lastAttemptAt;
}