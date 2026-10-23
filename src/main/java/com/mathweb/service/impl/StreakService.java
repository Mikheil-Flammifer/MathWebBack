package com.mathweb.service.impl;

import com.mathweb.dto.response.StreakResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class StreakService {

    private final JdbcTemplate jdbc;

    public StreakService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public StreakResponse getStreak(Long userId) {
        List<LocalDate> days = jdbc.query(
                "SELECT day FROM user_activity_days WHERE user_id = ? ORDER BY day",
                (rs, i) -> rs.getDate(1).toLocalDate(),
                userId);

        Set<LocalDate> set = new HashSet<>(days);
        LocalDate today = LocalDate.now(ActivityRecorder.ZONE);
        boolean activeToday = set.contains(today);

        // longest streak over all days (list is sorted ascending)
        int longest = 0, run = 0;
        LocalDate prev = null;
        for (LocalDate d : days) {
            run = (prev != null && prev.plusDays(1).equals(d)) ? run + 1 : 1;
            longest = Math.max(longest, run);
            prev = d;
        }

        // current streak: walk back from today (or yesterday if today is still empty)
        LocalDate cursor = activeToday ? today : today.minusDays(1);
        int current = 0;
        while (set.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }

        LocalDate from = today.minusDays(29);
        List<LocalDate> last30 = days.stream()
                .filter(d -> !d.isBefore(from) && !d.isAfter(today))
                .toList();

        return StreakResponse.builder()
                .currentStreak(current)
                .longestStreak(longest)
                .activeToday(activeToday)
                .streakAtRisk(current > 0 && !activeToday)
                .totalActiveDays(days.size())
                .last30Days(last30)
                .build();
    }
}