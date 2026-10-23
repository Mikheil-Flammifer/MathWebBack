package com.mathweb.service.impl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Component
public class ActivityRecorder {

    // Day boundary for streaks. Change to ZoneId.of("Asia/Tbilisi") if you prefer.
    public static final ZoneId ZONE = ZoneOffset.UTC;

    private final JdbcTemplate jdbc;

    public ActivityRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Marks today as an active day for the user (no-op if already marked). */
    public void record(Long userId) {
        jdbc.update(
                "INSERT INTO user_activity_days (user_id, day) VALUES (?, ?) ON CONFLICT DO NOTHING",
                userId, java.sql.Date.valueOf(LocalDate.now(ZONE)));
    }
}