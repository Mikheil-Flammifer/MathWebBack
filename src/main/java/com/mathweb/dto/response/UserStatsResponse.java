package com.mathweb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsResponse {

    private Long userId;
    private String fullName;
    private long totalXpEarned;
    private long problemsSolved;
    private long questsCompleted;
    private long questsInProgress;
    private long totalProblemsAttempted;
    private double averageAccuracy;  // correct / total attempts %
    private long videosWatched;
    private long commentsPosted;
}