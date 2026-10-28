package org.example.ride_hailing_logistics.driver.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.common.domain.VehicleType;

public record VehicleRequest(
        @NotNull VehicleType vehicleType,
        @NotBlank @Size(max = 15) String plateNumber,
        @NotBlank @Size(max = 50) String brand,
        @NotBlank @Size(max = 50) String model,
        @NotBlank @Size(max = 30) String color,
        @Min(1990) @Max(2100) int manufactureYear) {}
