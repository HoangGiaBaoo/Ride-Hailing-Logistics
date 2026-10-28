package org.example.ride_hailing_logistics.driver.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateDriverProfileRequest(
        @NotBlank @Size(max = 30) String licenseNumber,
        @NotBlank @Size(max = 10) String licenseClass,
        @NotNull LocalDate licenseExpiry,
        @NotBlank @Size(min = 9, max = 20) String nationalIdNumber,
        @NotBlank @Size(max = 255) String address) {}
