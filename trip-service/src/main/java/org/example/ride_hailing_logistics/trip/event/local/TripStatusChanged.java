package org.example.ride_hailing_logistics.trip.event.local;

import org.example.ride_hailing_logistics.trip.enums.TripStatus;
import java.time.Instant;
import java.util.UUID;

public record TripStatusChanged(UUID tripId, String code, UUID customerId, UUID driverId, TripStatus oldStatus,
                                TripStatus newStatus, long version, Instant occurredAt) {}
