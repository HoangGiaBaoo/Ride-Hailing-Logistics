package org.example.ride_hailing_logistics.identity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.identity.IdentityDtos.LoginRequest;
import org.example.ride_hailing_logistics.identity.IdentityDtos.MessageResponse;
import org.example.ride_hailing_logistics.identity.IdentityDtos.PasswordResetConfirmRequest;
import org.example.ride_hailing_logistics.identity.IdentityDtos.PasswordResetRequest;
import org.example.ride_hailing_logistics.identity.IdentityDtos.RefreshRequest;
import org.example.ride_hailing_logistics.identity.IdentityDtos.RegisterRequest;
import org.example.ride_hailing_logistics.identity.IdentityDtos.TokenResponse;
import org.example.ride_hailing_logistics.identity.IdentityDtos.UserResponse;
import org.springframework.http.HttpStatus;
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
    public ApiResponse<MessageResponse> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
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
