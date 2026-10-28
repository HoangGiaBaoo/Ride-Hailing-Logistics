package org.example.ride_hailing_logistics.driver.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuspendRequest(@NotBlank @Size(max = 500) String reason) {}
