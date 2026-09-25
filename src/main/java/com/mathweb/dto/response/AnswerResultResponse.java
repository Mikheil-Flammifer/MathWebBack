package com.mathweb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerResultResponse {

    private Boolean correct;
    private Integer attemptsUsed;
    private Integer attemptsRemaining;
    private Boolean answerRevealed;   // true when all attempts exhausted
    private String correctAnswer;     // only populated when answerRevealed = true
    private String explanation;       // only populated when answerRevealed = true
    private String explanationImagePath;
    private Integer xpEarned;         // only populated when correct = true
    private Boolean questCompleted;   // true if this was the last problem in the quest
}