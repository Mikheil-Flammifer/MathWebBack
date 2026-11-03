
package com.mathweb.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsrfHeaderFilterTest {

    @InjectMocks
    private CsrfHeaderFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @Test
    void testShouldNotFilter_GET() {
        when(request.getMethod()).thenReturn("GET");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void testShouldNotFilter_HEAD() {
        when(request.getMethod()).thenReturn("HEAD");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void testShouldNotFilter_OPTIONS() {
        when(request.getMethod()).thenReturn("OPTIONS");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void testShouldNotFilter_TRACE() {
        when(request.getMethod()).thenReturn("TRACE");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void testShouldNotFilter_POST() {
        when(request.getMethod()).thenReturn("POST");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void testShouldNotFilter_PUT() {
        when(request.getMethod()).thenReturn("PUT");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void testShouldNotFilter_PATCH() {
        when(request.getMethod()).thenReturn("PATCH");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void testDoFilterInternal_NoAuthCookie_PassesThrough()
            throws ServletException, IOException {

        when(request.getCookies()).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void testDoFilterInternal_HasAccessCookie_WithXRequestedWith()
            throws ServletException, IOException {

        Cookie accessCookie =
                new Cookie(AuthCookieService.ACCESS, "token");

        when(request.getCookies())
                .thenReturn(new Cookie[]{accessCookie});
        when(request.getHeader("X-Requested-With"))
                .thenReturn("XMLHttpRequest");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void testDoFilterInternal_HasAccessCookie_NoXRequestedWith()
            throws ServletException, IOException {

        Cookie accessCookie =
                new Cookie(AuthCookieService.ACCESS, "token");

        when(request.getCookies())
                .thenReturn(new Cookie[]{accessCookie});
        when(request.getHeader("X-Requested-With"))
                .thenReturn(null);

        StringWriter stringWriter = new StringWriter();
        when(response.getWriter())
                .thenReturn(new PrintWriter(stringWriter));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType("application/json");
        verify(chain, never()).doFilter(request, response);

        assertEquals(
                "{\"success\":false,\"message\":\"Missing required request header\"}",
                stringWriter.toString()
        );
    }

    @Test
    void testDoFilterInternal_HasRefreshCookie_NoXRequestedWith()
            throws ServletException, IOException {

        Cookie refreshCookie =
                new Cookie(AuthCookieService.REFRESH, "token");

        when(request.getCookies())
                .thenReturn(new Cookie[]{refreshCookie});
        when(request.getHeader("X-Requested-With"))
                .thenReturn(null);

        StringWriter stringWriter = new StringWriter();
        when(response.getWriter())
                .thenReturn(new PrintWriter(stringWriter));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType("application/json");
        verify(chain, never()).doFilter(request, response);

        assertEquals(
                "{\"success\":false,\"message\":\"Missing required request header\"}",
                stringWriter.toString()
        );
    }

    @Test
    void testDoFilterInternal_NoCookiesAndNoHeader_PassesThrough()
            throws ServletException, IOException {

        when(request.getCookies()).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
