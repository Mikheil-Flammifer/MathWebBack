package com.mathweb.controller;

import com.mathweb.dto.request.*;
import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.AuthResponse;
import com.mathweb.dto.response.UserResponse;
import com.mathweb.exception.BadRequestException;
import com.mathweb.security.AuthCookieService;
import com.mathweb.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService cookieService;

    public AuthController(AuthService authService, AuthCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        try {
            authService.register(request);
        } catch (DataIntegrityViolationException ignored) {
            // A concurrent request may have created the same email first.
            // Return the same generic response to avoid email enumeration.
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(
                        "If the address can be registered, "
                                + "an e-mail with next steps will be sent."
                ));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully"));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Void>> resendOtp(
            @Valid @RequestBody ResendOtpRequest request
    ) {
        authService.resendOtp(request);

        return ResponseEntity.accepted()
                .body(ApiResponse.success(
                        "If an eligible account exists, "
                                + "an e-mail with next steps will be sent."
                ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);
        cookieService.write(response, authResponse.getAccessToken(), authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Login successful", withoutTokens(authResponse)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        String token = cookieService.read(request, AuthCookieService.REFRESH).orElse(null);
        if (token == null || token.length() > 2000) {
            throw new BadRequestException("Refresh token is missing");
        }

        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken(token);

        AuthResponse authResponse = authService.refreshToken(refreshRequest);
        cookieService.write(response, authResponse.getAccessToken(), authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", withoutTokens(authResponse)));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "If this email exists, a password reset code has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        String access = cookieService.read(request, AuthCookieService.ACCESS).orElse(null);
        String refresh = cookieService.read(request, AuthCookieService.REFRESH).orElse(null);
        authService.logout(access);
        authService.revokeRefreshToken(refresh);
        cookieService.clear(response);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    private AuthResponse withoutTokens(AuthResponse r) {
        return AuthResponse.builder().user(r.getUser()).build();
    }
}