package com.mathweb.service.impl;

import com.mathweb.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService(jdbc);
    }

    @Test
    void checkLoginRate_rejectsWhenDatabaseRateLimitIsExceeded() {
        when(jdbc.queryForObject(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
        )).thenReturn(null);

        assertThatThrownBy(() ->
                service.checkLoginRate("student@example.com")
        )
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Too many login attempts. Please wait 15 minutes.");
    }
}