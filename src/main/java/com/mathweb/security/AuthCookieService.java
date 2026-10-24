package com.mathweb.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class AuthCookieService {

    public static final String ACCESS = "access_token";
    public static final String REFRESH = "refresh_token";

    private static final String ACCESS_PATH = "/";
    private static final String REFRESH_PATH = "/api/auth";

    @Value("${app.cookies.secure:false}")
    private boolean secure;

    @Value("${app.cookies.same-site:Lax}")
    private String sameSite;

    @Value("${app.cookies.access-max-age-seconds:86400}")
    private long accessMaxAgeSeconds;

    @Value("${app.cookies.refresh-max-age-seconds:604800}")
    private long refreshMaxAgeSeconds;

    public void write(HttpServletResponse response, String accessToken, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                build(ACCESS, accessToken, ACCESS_PATH, accessMaxAgeSeconds).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                build(REFRESH, refreshToken, REFRESH_PATH, refreshMaxAgeSeconds).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                build(ACCESS, "", ACCESS_PATH, 0).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                build(REFRESH, "", REFRESH_PATH, 0).toString());
    }

    public Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie build(String name, String value, String path, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }
}
