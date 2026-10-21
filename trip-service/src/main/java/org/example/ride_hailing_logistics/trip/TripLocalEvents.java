package org.example.ride_hailing_logistics.trip;

import java.time.Instant;
import java.util.UUID;

/**
 * Event trong tien trinh, phat SAU COMMIT cho realtime va kich hoat dispatch.
 * Khong phai nguon su that: mat event nay thi client van lay snapshot qua API (FR-RT-007).
 */
public final class TripLocalEvents {

    private TripLocalEvents() {
    }

    public record TripStatusChanged(UUID tripId, String code, UUID customerId, UUID driverId, TripStatus oldStatus,
                                    TripStatus newStatus, long version, Instant occurredAt) {}

    public record DispatchRequested(UUID tripId) {}

    public record OfferCreated(UUID offerId, UUID tripId, UUID driverId, String serviceType, double pickupLat,
                               double pickupLng, String pickupAddress, double dropoffLat, double dropoffLng,
                               String dropoffAddress, int estimatedPickupDistanceMeters, java.math.BigDecimal estimatedFare,
                               String currency, Instant expiresAt) {}

    public record OfferClosed(UUID offerId, UUID tripId, UUID driverId, String reason) {}
}
