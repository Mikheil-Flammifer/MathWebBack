package com.mathweb.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathweb.exception.TooManyRequestsException;
import com.mathweb.service.impl.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class EndpointRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public EndpointRateLimitFilter(
            RateLimitService rateLimitService,
            ObjectMapper objectMapper
    ) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        String method = request.getMethod();
        String path = request.getRequestURI();

        boolean auth = "POST".equals(method)
                && (path.equals("/api/auth/register")
                || path.equals("/api/auth/verify-otp")
                || path.equals("/api/auth/reset-password"));

        boolean checkout = "POST".equals(method)
                && path.equals("/api/paddle/checkout");

        boolean comments = (
                "POST".equals(method)
                        && (path.equals("/api/comments")
                        || path.matches("/api/comments/\\d+/(upvote|downvote)"))
        ) || (
                ("PUT".equals(method) || "DELETE".equals(method))
                        && path.matches("/api/comments/\\d+")
        );

        boolean stream = "GET".equals(method)
                && path.matches("/api/videos/\\d+/stream");

        return !(auth || checkout || comments || stream);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        try {
            String path = request.getRequestURI();

            if (path.equals("/api/paddle/checkout")) {
                rateLimitService.checkIpRate(
                        "checkout", 20,
                        "Too many checkout requests. Please wait."
                );
            } else if (path.startsWith("/api/comments")) {
                rateLimitService.checkIpRate(
                        "comment", 30,
                        "Too many comment requests. Please wait."
                );
            } else if (path.contains("/stream")) {
                rateLimitService.checkIpRate(
                        "stream", 120,
                        "Too many streaming requests. Please wait."
                );
            } else {
                rateLimitService.checkIpRate(
                        "auth", 20,
                        "Too many requests. Please wait."
                );
            }

            chain.doFilter(request, response);
        } catch (TooManyRequestsException e) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(
                    "Retry-After",
                    Long.toString(e.getRetryAfterSeconds())
            );
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            objectMapper.writeValue(response.getOutputStream(), Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}