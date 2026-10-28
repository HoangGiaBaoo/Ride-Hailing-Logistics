package org.example.ride_hailing_logistics.trip.event;

import java.time.Instant;
import java.util.UUID;

public record TripCompleted(UUID tripId, String tripCode, UUID customerId, UUID driverId, UUID quoteId,
                            String serviceType, String paymentMethod, int distanceMeters, int durationSeconds,
                            Instant completedAt) {}
