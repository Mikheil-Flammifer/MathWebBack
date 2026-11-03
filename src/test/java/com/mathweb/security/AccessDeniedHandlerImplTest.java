
package com.mathweb.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessDeniedHandlerImplTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private AccessDeniedHandlerImpl handler;

    private static final String EXPECTED_MESSAGE =
            "{\"success\":false,\"message\":\"Access denied: You don't have permission to access this resource\"}";

    @Test
    void testHandle_SetsStatus403() throws IOException {
        AccessDeniedException ex = new AccessDeniedException("Test");
        StringWriter sw = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(sw));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"success\":false}");

        handler.handle(request, response, ex);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void testHandle_SetsContentType() throws IOException {
        AccessDeniedException ex = new AccessDeniedException("Test");
        StringWriter sw = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(sw));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"success\":false}");

        handler.handle(request, response, ex);

        verify(response).setContentType("application/json");
    }

    @Test
    void testHandle_WritesFixedMessage() throws IOException {
        AccessDeniedException ex =
                new AccessDeniedException("Custom message");

        StringWriter sw = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(sw));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn(EXPECTED_MESSAGE);

        handler.handle(request, response, ex);

        assertThat(sw.toString()).isEqualTo(EXPECTED_MESSAGE);
    }

    @Test
    void testHandle_MessageIgnoresExceptionMessage() throws IOException {
        AccessDeniedException ex1 =
                new AccessDeniedException("Message 1");

        AccessDeniedException ex2 =
                new AccessDeniedException("Message 2");

        StringWriter sw = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(sw));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn(EXPECTED_MESSAGE);

        handler.handle(request, response, ex1);
        handler.handle(request, response, ex2);

        assertThat(sw.toString())
                .isEqualTo(EXPECTED_MESSAGE + EXPECTED_MESSAGE);
    }
}
