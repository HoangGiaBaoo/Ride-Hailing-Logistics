package org.example.ride_hailing_logistics.driver.dto.response;

import org.example.ride_hailing_logistics.common.domain.VehicleType;
import java.util.UUID;

/** Thong tin tai xe duoc chup vao chuyen khi ghep (UI-CUS-005). */
public record DriverSnapshotResponse(UUID driverId, String fullName, String phone, UUID vehicleId, VehicleType vehicleType,
                             String plateNumber, String vehicleDescription) {}
