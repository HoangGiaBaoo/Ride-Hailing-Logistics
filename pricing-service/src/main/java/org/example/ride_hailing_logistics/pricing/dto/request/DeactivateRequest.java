package org.example.ride_hailing_logistics.pricing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeactivateRequest(@NotBlank @Size(max = 500) String reason) {}
