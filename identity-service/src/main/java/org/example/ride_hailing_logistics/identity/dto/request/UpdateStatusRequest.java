package org.example.ride_hailing_logistics.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.identity.enums.UserStatus;

public record UpdateStatusRequest(@NotNull UserStatus status, @NotBlank @Size(max = 500) String reason) {}
