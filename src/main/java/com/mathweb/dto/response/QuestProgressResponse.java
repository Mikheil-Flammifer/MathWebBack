package com.mathweb.dto.response;

import com.mathweb.enums.QuestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestProgressResponse {

    private Long questId;
    private String questTitle;
    private QuestStatus status;
    private Integer problemsSolved;
    private Integer totalProblems;
    private Double progressPercentage;
    private Integer xpEarned;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}