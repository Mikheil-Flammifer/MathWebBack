
package com.mathweb.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthEntryPointTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private AuthEntryPoint authEntryPoint;

    @Test
    void testCommence_SetsStatus401() throws IOException {
        AuthenticationException ex = new AuthenticationException("Test") {};
        StringWriter sw = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(sw));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"success\":false}");

        authEntryPoint.commence(request, response, ex);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void testCommence_SetsContentType() throws IOException {
        AuthenticationException ex = new AuthenticationException("Test") {};
        StringWriter sw = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(sw));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"success\":false}");

        authEntryPoint.commence(request, response, ex);

        verify(response).setContentType("application/json");
    }

    @Test
    void testCommence_WritesErrorMessage() throws IOException {
        AuthenticationException ex =
                new AuthenticationException("Invalid credentials") {};

        StringWriter sw = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(sw));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"success\":false,\"message\":\"Unauthorized: Invalid credentials\"}");

        authEntryPoint.commence(request, response, ex);

        assertThat(sw.toString())
                .isEqualTo("{\"success\":false,\"message\":\"Unauthorized: Invalid credentials\"}");
    }

    @Test
    void testCommence_WithNullMessage() throws IOException {
        AuthenticationException ex = new AuthenticationException(null) {};
        StringWriter sw = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(sw));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"success\":false,\"message\":\"Unauthorized: null\"}");

        authEntryPoint.commence(request, response, ex);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
