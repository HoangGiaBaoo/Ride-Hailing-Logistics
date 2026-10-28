package org.example.ride_hailing_logistics.trip.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tai xe chi thay thong tin can de quyet dinh nhan chuyen — khong ten/SDT khach (FR-MAT-008, FR-LOC-012). */
public record OfferResponse(UUID offerId, UUID tripId, String serviceType, double pickupLat, double pickupLng,
                            String pickupAddress, String dropoffAddress, int estimatedPickupDistanceMeters,
                            BigDecimal estimatedFare, String currency, Instant expiresAt) {}
