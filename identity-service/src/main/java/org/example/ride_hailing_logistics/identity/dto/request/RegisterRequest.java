package org.example.ride_hailing_logistics.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.identity.enums.SelfRegistrationRole;

public record RegisterRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 20) String phone,
        // Chinh sach mat khau (FR-IAM-003): 8-72 ky tu (gioi han cua BCrypt), co chu va so
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Mật khẩu phải có cả chữ và số")
        String password,
        @NotBlank @Size(max = 100) String fullName,
        @NotNull SelfRegistrationRole role) {}
