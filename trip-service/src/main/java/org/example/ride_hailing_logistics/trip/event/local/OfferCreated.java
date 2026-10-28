package org.example.ride_hailing_logistics.trip.event.local;

import java.time.Instant;
import java.util.UUID;

public record OfferCreated(UUID offerId, UUID tripId, UUID driverId, String serviceType, double pickupLat,
                           double pickupLng, String pickupAddress, double dropoffLat, double dropoffLng,
                           String dropoffAddress, int estimatedPickupDistanceMeters, java.math.BigDecimal estimatedFare,
                           String currency, Instant expiresAt) {}
