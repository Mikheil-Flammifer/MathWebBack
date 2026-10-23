package com.mathweb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolutionResponse {
    private String correctAnswer;
    private String explanation;
    private String explanationImagePath;
    private Integer xpPenalty;
    private Long correctOptionId;      // MULTIPLE_CHOICE only
    private Boolean questCompleted;
}