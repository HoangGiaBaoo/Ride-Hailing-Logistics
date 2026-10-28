package org.example.ride_hailing_logistics.trip.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecipientRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 20) String phone) {}
