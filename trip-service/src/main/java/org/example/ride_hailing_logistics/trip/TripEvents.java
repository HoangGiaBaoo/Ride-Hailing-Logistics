package org.example.ride_hailing_logistics.trip;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Payload event lien mien cua trip qua outbox (SRS §5.6). Them truong = them cuoi; khong doi nghia truong cu (NFR-COMP-002). */
public final class TripEvents {

    public static final String TRIP_REQUESTED = "TripRequested";
    public static final String TRIP_ACCEPTED = "TripAccepted";
    public static final String TRIP_COMPLETED = "TripCompleted";
    public static final String TRIP_CANCELLED = "TripCancelled";

    private TripEvents() {
    }

    public record TripRequested(UUID tripId, String serviceType, String vehicleType, double pickupLat, double pickupLng,
                                UUID quoteId, Instant requestedAt) {}

    public record TripAccepted(UUID tripId, UUID driverId, Instant acceptedAt) {}

    public record TripCompleted(UUID tripId, String tripCode, UUID customerId, UUID driverId, UUID quoteId,
                                String serviceType, String paymentMethod, int distanceMeters, int durationSeconds,
                                Instant completedAt) {}

    public record TripCancelled(UUID tripId, String tripCode, UUID customerId, UUID driverId, String serviceType,
                                String paymentMethod, String actor, String reason, BigDecimal feeAmount, String currency,
                                String policyVersion, Instant cancelledAt) {}
}
