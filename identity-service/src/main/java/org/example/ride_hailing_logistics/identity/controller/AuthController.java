package org.example.ride_hailing_logistics.identity.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.identity.dto.request.LoginRequest;
import org.example.ride_hailing_logistics.identity.dto.request.PasswordResetConfirmRequest;
import org.example.ride_hailing_logistics.identity.dto.request.PasswordResetRequest;
import org.example.ride_hailing_logistics.identity.dto.request.RefreshRequest;
import org.example.ride_hailing_logistics.identity.dto.request.RegisterRequest;
import org.example.ride_hailing_logistics.identity.dto.response.MessageResponse;
import org.example.ride_hailing_logistics.identity.dto.response.TokenResponse;
import org.example.ride_hailing_logistics.identity.dto.response.UserResponse;
import org.example.ride_hailing_logistics.identity.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<MessageResponse> logout(@Valid @RequestBody RefreshRequest request,
                                               @AuthenticationPrincipal Jwt accessToken) {
        // Co gui kem access token thi dua jti vao danh sach den; khong co van thu hoi refresh token
        authService.logout(request.refreshToken(), accessToken == null ? null : accessToken.getId(),
                accessToken == null ? null : accessToken.getExpiresAt());
        return ApiResponse.ok(new MessageResponse("Đã đăng xuất."));
    }

    @PostMapping("/password-reset/request")
    public ApiResponse<MessageResponse> requestReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.login());
        return ApiResponse.ok(new MessageResponse("Nếu tài khoản tồn tại, mã đặt lại mật khẩu đã được gửi."));
    }

    @PostMapping("/password-reset/confirm")
    public ApiResponse<MessageResponse> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request.token(), request.newPassword());
        return ApiResponse.ok(new MessageResponse("Đã đặt lại mật khẩu, vui lòng đăng nhập lại."));
    }
}
