package com.mathweb.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DateUtilTest {

    @Test
    void testFormat_NormalDateTime() {
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 14, 30);
        String formatted = DateUtil.format(dateTime);
        assertEquals("15 Jan 2024 14:30", formatted);
    }

    @Test
    void testFormat_NullInput() {
        String formatted = DateUtil.format(null);
        assertEquals("", formatted);
    }

    @Test
    void testFormatDate_NormalDateTime() {
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 14, 30);
        String formatted = DateUtil.formatDate(dateTime);
        assertEquals("15 Jan 2024", formatted);
    }

    @Test
    void testFormatDate_NullInput() {
        String formatted = DateUtil.formatDate(null);
        assertEquals("", formatted);
    }

    @Test
    void testTimeAgo_JustNow() {
        LocalDateTime now = LocalDateTime.now();
        String result = DateUtil.timeAgo(now);
        assertEquals("just now", result);
    }

    @Test
    void testTimeAgo_SecondsAgo() {
        LocalDateTime dateTime = LocalDateTime.now().minusSeconds(30);
        String result = DateUtil.timeAgo(dateTime);
        assertEquals("just now", result);
    }

    @Test
    void testTimeAgo_MinutesAgo() {
        LocalDateTime dateTime = LocalDateTime.now().minusMinutes(5);
        String result = DateUtil.timeAgo(dateTime);
        assertEquals("5 minutes ago", result);
    }

    @Test
    void testTimeAgo_SingleMinuteAgo() {
        LocalDateTime dateTime = LocalDateTime.now().minusMinutes(1);
        String result = DateUtil.timeAgo(dateTime);
        assertEquals("1 minutes ago", result);
    }

    @Test
    void testTimeAgo_HoursAgo() {
        LocalDateTime dateTime = LocalDateTime.now().minusHours(3);
        String result = DateUtil.timeAgo(dateTime);
        assertEquals("3 hours ago", result);
    }

    @Test
    void testTimeAgo_DaysAgo() {
        LocalDateTime dateTime = LocalDateTime.now().minusDays(2);
        String result = DateUtil.timeAgo(dateTime);
        assertEquals("2 days ago", result);
    }

    @Test
    void testTimeAgo_MonthsAgo() {
        LocalDateTime dateTime = LocalDateTime.now().minusMonths(6);
        String result = DateUtil.timeAgo(dateTime);
        assertEquals("6 months ago", result);
    }

    @Test
    void testTimeAgo_YearsAgo() {
        LocalDateTime dateTime = LocalDateTime.now().minusYears(2);
        String result = DateUtil.timeAgo(dateTime);
        assertEquals("2 years ago", result);
    }

    @Test
    void testTimeAgo_NullInput() {
        String result = DateUtil.timeAgo(null);
        assertEquals("", result);
    }

    @Test
    void testIsExpired_FutureDate() {
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        assertFalse(DateUtil.isExpired(future));
    }

    @Test
    void testIsExpired_PastDate() {
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        assertTrue(DateUtil.isExpired(past));
    }

    @Test
    void testIsExpired_JustInFuture() {
        LocalDateTime soon = LocalDateTime.now().plusSeconds(5);
        assertFalse(DateUtil.isExpired(soon));
    }

    @Test
    void testIsExpired_NullInput() {
        assertTrue(DateUtil.isExpired(null));
    }

    @Test
    void testTimeAgo_EdgeCase59Seconds() {
        LocalDateTime dateTime = LocalDateTime.now().minusSeconds(59);
        String result = DateUtil.timeAgo(dateTime);
        assertEquals("just now", result);
    }

    @Test
    void testTimeAgo_EdgeCase60Seconds() {
        LocalDateTime dateTime = LocalDateTime.now().minusSeconds(60);
        String result = DateUtil.timeAgo(dateTime);
        assertEquals("1 minutes ago", result);
    }

    @Test
    void testTimeAgo_EdgeCase59Minutes() {
        LocalDateTime dateTime = LocalDateTime.now().minusMinutes(59);
        String result = DateUtil.timeAgo(dateTime);
        assertEquals("59 minutes ago", result);
    }

    @Test
    void testTimeAgo_EdgeCase60Minutes() {
        LocalDateTime dateTime = LocalDateTime.now().minusMinutes(60);
        String result = DateUtil.timeAgo(dateTime);
        assertEquals("1 hours ago", result);
    }

    @Test
    void testTimeAgo_EdgeCase23Hours() {
        LocalDateTime dateTime = LocalDateTime.now().minusHours(23);
        String result = DateUtil.timeAgo(dateTime);
        assertEquals("23 hours ago", result);
    }

    @Test
    void testTimeAgo_EdgeCase24Hours() {
        LocalDateTime dateTime = LocalDateTime.now().minusHours(24);
        String result = DateUtil.timeAgo(dateTime);
        assertEquals("1 days ago", result);
    }
}