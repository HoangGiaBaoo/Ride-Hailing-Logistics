package org.example.ride_hailing_logistics.identity.service;

import org.example.ride_hailing_logistics.identity.dto.request.LoginRequest;
import org.example.ride_hailing_logistics.identity.dto.request.RegisterRequest;
import org.example.ride_hailing_logistics.identity.dto.response.TokenResponse;
import org.example.ride_hailing_logistics.identity.dto.response.UserResponse;

import java.time.Instant;

/** Dang ky, dang nhap, xoay vong refresh token, dang xuat, dat lai mat khau (FR-IAM-001..007). */
public interface AuthService {

    UserResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(String rawRefreshToken);

    void logout(String rawRefreshToken, String accessJti, Instant accessExpiresAt);

    void requestPasswordReset(String login);

    void confirmPasswordReset(String rawToken, String newPassword);
}
