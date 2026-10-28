package org.example.ride_hailing_logistics.trip.dto.response;

import java.util.UUID;

public record DriverInfoResponse(UUID driverId, String name, String maskedPhone, String vehiclePlate, String vehicleDescription) {}
