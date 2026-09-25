package com.mathweb.dto.response;

import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.QuestStatus;
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
public class QuestResponse {

    private Long id;
    private String title;
    private String description;
    private String iconUrl;
    private DifficultyLevel difficultyLevel;
    private Integer positionX;
    private Integer positionY;
    private Boolean published;
    private Integer xpReward;
    private List<Long> prerequisiteIds;
    private List<Long> dependentIds;
    private int totalProblems;
    private int totalVideos;

    // Per-user fields (populated when user is logged in)
    private QuestStatus userStatus;
    private Integer userProblemsSolved;
    private Double userProgressPercentage;

    private LocalDateTime createdAt;
}