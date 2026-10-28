package org.example.ride_hailing_logistics.driver.dto.response;

import org.example.ride_hailing_logistics.common.domain.VehicleType;

import java.util.UUID;

public record VehicleResponse(UUID id, VehicleType vehicleType, String plateNumber, String brand, String model,
                              String color, int manufactureYear) {}
