package org.example.ride_hailing_logistics.trip.client.dto;

import org.example.ride_hailing_logistics.common.domain.VehicleType;

import java.util.UUID;

public record DriverSnapshotResponse(UUID driverId, String fullName, String phone, UUID vehicleId, VehicleType vehicleType,
                                     String plateNumber, String vehicleDescription) {}
