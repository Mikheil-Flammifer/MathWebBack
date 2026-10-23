package com.mathweb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreakResponse {
    private Integer currentStreak;
    private Integer longestStreak;
    private Boolean activeToday;
    private Boolean streakAtRisk;
    private Integer totalActiveDays;
    private List<LocalDate> last30Days;   // active days in the last 30 days, oldest first
}