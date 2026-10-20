package org.example.ride_hailing_logistics.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.common.security.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** DTO cua mien identity. Record chi chua truong duoc phep gui len -> chong mass assignment (DR-VAL-005). */
public final class IdentityDtos {

    private IdentityDtos() {
    }

    public enum SelfRegistrationRole { CUSTOMER, DRIVER }

    public record RegisterRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 20) String phone,
            // Chinh sach mat khau (FR-IAM-003): 8-72 ky tu (gioi han cua BCrypt), co chu va so
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Mật khẩu phải có cả chữ và số")
            String password,
            @NotBlank @Size(max = 100) String fullName,
            @NotNull SelfRegistrationRole role) {}

    public record LoginRequest(@NotBlank @Size(max = 254) String login, @NotBlank @Size(max = 72) String password) {}

    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {}

    public record PasswordResetRequest(@NotBlank @Size(max = 254) String login) {}

    public record PasswordResetConfirmRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Mật khẩu phải có cả chữ và số")
            String newPassword) {}

    public record UpdateProfileRequest(@NotBlank @Size(max = 100) String fullName) {}

    public record UpdateRolesRequest(@NotEmpty Set<Role> roles, @NotBlank @Size(max = 500) String reason) {}

    public record UpdateStatusRequest(@NotNull UserStatus status, @NotBlank @Size(max = 500) String reason) {}

    public record TokenResponse(String accessToken, Instant accessTokenExpiresAt, String refreshToken,
                                Instant refreshTokenExpiresAt, String tokenType, UserResponse user) {}

    public record UserResponse(UUID id, String email, String phone, String fullName, UserStatus status, Set<Role> roles,
                               Instant createdAt) {
        static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getPhone(), u.getFullName(), u.getStatus(),
                    Set.copyOf(u.getRoles()), u.getCreatedAt());
        }
    }

    public record MessageResponse(String message) {}
}
