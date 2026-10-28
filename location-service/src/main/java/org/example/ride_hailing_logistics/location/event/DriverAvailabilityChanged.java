package org.example.ride_hailing_logistics.location.event;

import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.location.enums.DriverAvailability;

import java.time.Instant;
import java.util.UUID;

/** Payload event trip-service phat khi trang thai kha dung tai xe doi. */
public record DriverAvailabilityChanged(UUID driverId, DriverAvailability oldStatus, DriverAvailability newStatus,
                                        VehicleType vehicleType, long version, Instant occurredAt) {}
