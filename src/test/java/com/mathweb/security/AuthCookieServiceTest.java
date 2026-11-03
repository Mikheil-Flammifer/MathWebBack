package com.mathweb.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthCookieServiceTest {

    @InjectMocks
    private AuthCookieService cookieService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cookieService, "secure", false);
        ReflectionTestUtils.setField(cookieService, "sameSite", "Lax");
        ReflectionTestUtils.setField(cookieService, "accessMaxAgeSeconds", 900L);
        ReflectionTestUtils.setField(cookieService, "refreshMaxAgeSeconds", 604800L);
    }

    @Test
    void testWrite_AddsBothCookies() {
        cookieService.write(response, "access_token", "refresh_token");
        verify(response, times(2)).addHeader(eq("Set-Cookie"), anyString());
    }

    @Test
    void testClear_AddsBothCookiesWithZeroAge() {
        cookieService.clear(response);
        verify(response, times(2)).addHeader(eq("Set-Cookie"), anyString());
    }

    @Test
    void testRead_AccessTokenCookie() {
        Cookie accessCookie = new Cookie(AuthCookieService.ACCESS, "access_token_123");
        when(request.getCookies()).thenReturn(new Cookie[]{accessCookie});

        Optional<String> result = cookieService.read(request, AuthCookieService.ACCESS);
        assertTrue(result.isPresent());
        assertEquals("access_token_123", result.get());
    }

    @Test
    void testRead_RefreshTokenCookie() {
        Cookie refreshCookie = new Cookie(AuthCookieService.REFRESH, "refresh_token_456");
        when(request.getCookies()).thenReturn(new Cookie[]{refreshCookie});

        Optional<String> result = cookieService.read(request, AuthCookieService.REFRESH);
        assertTrue(result.isPresent());
        assertEquals("refresh_token_456", result.get());
    }

    @Test
    void testRead_NoCookies() {
        when(request.getCookies()).thenReturn(null);
        assertFalse(cookieService.read(request, AuthCookieService.ACCESS).isPresent());
    }

    @Test
    void testRead_EmptyCookies() {
        when(request.getCookies()).thenReturn(new Cookie[0]);
        assertFalse(cookieService.read(request, AuthCookieService.ACCESS).isPresent());
    }

    @Test
    void testRead_CookieNotFound() {
        Cookie otherCookie = new Cookie("session", "value");
        when(request.getCookies()).thenReturn(new Cookie[]{otherCookie});
        assertFalse(cookieService.read(request, AuthCookieService.ACCESS).isPresent());
    }

    @Test
    void testRead_BlankCookieValue() {
        Cookie accessCookie = new Cookie(AuthCookieService.ACCESS, "   ");
        when(request.getCookies()).thenReturn(new Cookie[]{accessCookie});
        assertFalse(cookieService.read(request, AuthCookieService.ACCESS).isPresent());
    }

    @Test
    void testRead_NullCookieValue() {
        Cookie accessCookie = new Cookie(AuthCookieService.ACCESS, null);
        when(request.getCookies()).thenReturn(new Cookie[]{accessCookie});
        assertFalse(cookieService.read(request, AuthCookieService.ACCESS).isPresent());
    }

    @Test
    void testRead_EmptyCookieValue() {
        Cookie accessCookie = new Cookie(AuthCookieService.ACCESS, "");
        when(request.getCookies()).thenReturn(new Cookie[]{accessCookie});
        assertFalse(cookieService.read(request, AuthCookieService.ACCESS).isPresent());
    }
}