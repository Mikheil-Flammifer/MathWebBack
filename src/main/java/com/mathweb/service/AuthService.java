package com.mathweb.service;

import com.mathweb.dto.request.*;
import com.mathweb.dto.response.AuthResponse;
import com.mathweb.dto.response.UserResponse;

public interface AuthService {
    UserResponse register(RegisterRequest request);
    void verifyOtp(VerifyOtpRequest request);
    void resendOtp(ResendOtpRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    void logout(String token);
}