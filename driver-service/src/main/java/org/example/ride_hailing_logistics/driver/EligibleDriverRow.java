package org.example.ride_hailing_logistics.driver;

import org.example.ride_hailing_logistics.common.domain.VehicleType;

import java.util.UUID;

/** Ket qua loc ung vien du dieu kien nhan offer (FR-MAT-002). */
public record EligibleDriverRow(UUID driverId, UUID vehicleId, VehicleType vehicleType) {
}
