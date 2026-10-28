package org.example.ride_hailing_logistics.trip.dto;

import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.trip.enums.DriverAvailability;

/** Doc thang tu DB (khong qua persistence context) de lay version moi sau cap nhat co dieu kien. */
public record AvailabilityRow(DriverAvailability availability, VehicleType vehicleType, long version) {}
