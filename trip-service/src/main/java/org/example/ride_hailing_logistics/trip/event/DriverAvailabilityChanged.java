package org.example.ride_hailing_logistics.trip.event;

import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.trip.enums.DriverAvailability;

import java.time.Instant;
import java.util.UUID;

/**
 * Phat moi lan kha dung tai xe doi. version tang theo tung lan chuyen: location-service bo ban tin den tre
 * (Kafka giu thu tu theo khoa nhung consumer co the doc lai sau khi rebalance).
 */
public record DriverAvailabilityChanged(UUID driverId, DriverAvailability oldStatus, DriverAvailability newStatus,
                                        VehicleType vehicleType, long version, Instant occurredAt) {}
