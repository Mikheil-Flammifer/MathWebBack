package com.mathweb.service.impl;

import com.mathweb.dto.response.StreakResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Unit tests for {@link StreakService}. */
@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private JdbcTemplate jdbc;
    @InjectMocks private StreakService service;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ActivityRecorder.ZONE);
    }

    private void givenDays(LocalDate... days) {
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<LocalDate>>any(), eq(USER_ID)))
                .thenReturn(List.of(days));
    }

    @Test
    void getStreak_returnsZeros_whenNoActivity() {
        givenDays();

        StreakResponse r = service.getStreak(USER_ID);

        assertThat(r.getCurrentStreak()).isZero();
        assertThat(r.getLongestStreak()).isZero();
        assertThat(r.getActiveToday()).isFalse();
        assertThat(r.getStreakAtRisk()).isFalse();
        assertThat(r.getTotalActiveDays()).isZero();
        assertThat(r.getLast30Days()).isEmpty();
    }

    @Test
    void getStreak_countsConsecutiveDaysEndingToday() {
        givenDays(today.minusDays(2), today.minusDays(1), today);

        StreakResponse r = service.getStreak(USER_ID);

        assertThat(r.getCurrentStreak()).isEqualTo(3);
        assertThat(r.getLongestStreak()).isEqualTo(3);
        assertThat(r.getActiveToday()).isTrue();
        assertThat(r.getStreakAtRisk()).isFalse();
    }

    @Test
    void getStreak_keepsStreakAlive_butAtRisk_whenOnlyYesterdayActive() {
        givenDays(today.minusDays(2), today.minusDays(1));

        StreakResponse r = service.getStreak(USER_ID);

        assertThat(r.getCurrentStreak()).isEqualTo(2);
        assertThat(r.getActiveToday()).isFalse();
        assertThat(r.getStreakAtRisk()).isTrue();
    }

    @Test
    void getStreak_resetsCurrentStreak_whenLastActivityIsOlderThanYesterday() {
        givenDays(today.minusDays(5), today.minusDays(4), today.minusDays(3));

        StreakResponse r = service.getStreak(USER_ID);

        assertThat(r.getCurrentStreak()).isZero();
        assertThat(r.getLongestStreak()).isEqualTo(3);
        assertThat(r.getStreakAtRisk()).isFalse();
    }

    @Test
    void getStreak_longestStreakComesFromAnEarlierRun() {
        givenDays(
                today.minusDays(20), today.minusDays(19), today.minusDays(18), today.minusDays(17),
                today.minusDays(1), today);

        StreakResponse r = service.getStreak(USER_ID);

        assertThat(r.getCurrentStreak()).isEqualTo(2);
        assertThat(r.getLongestStreak()).isEqualTo(4);
        assertThat(r.getTotalActiveDays()).isEqualTo(6);
    }

    @Test
    void getStreak_gapBreaksTheRun() {
        givenDays(today.minusDays(3), today.minusDays(1), today);

        StreakResponse r = service.getStreak(USER_ID);

        assertThat(r.getCurrentStreak()).isEqualTo(2);
        assertThat(r.getLongestStreak()).isEqualTo(2);
    }

    @Test
    void getStreak_last30Days_includesOnlyTheWindow_oldestFirst() {
        LocalDate old = today.minusDays(30);
        LocalDate edge = today.minusDays(29);
        givenDays(old, edge, today);

        StreakResponse r = service.getStreak(USER_ID);

        assertThat(r.getLast30Days()).containsExactly(edge, today);
        assertThat(r.getTotalActiveDays()).isEqualTo(3);
    }
}