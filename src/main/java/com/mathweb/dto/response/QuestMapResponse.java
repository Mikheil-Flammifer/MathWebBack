package com.mathweb.dto.response;

import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.NodeStatus;
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
public class QuestMapResponse {

    private Long questId;
    private String title;
    private DifficultyLevel difficultyLevel;
    private boolean questUnlocked;
    private List<MapNode> nodes;
    private List<MapEdge> edges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapNode {
        private Long problemId;
        private Integer x;
        private Integer y;
        private boolean start;
        private String nodeIcon;
        private Integer orderIndex;
        private Integer xpReward;
        private Long categoryId;          // territory (subcategory)
        private String categoryName;
        private Long mainCategoryId;      // algebra / geometry / ...
        private String mainCategoryName;
        private NodeStatus status;
        private Integer attemptsUsed;
        private Integer maxAttempts;
        private LocalDateTime retryAvailableAt; // set while cooling down
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapEdge {
        private Long from;
        private Long to;
    }
}