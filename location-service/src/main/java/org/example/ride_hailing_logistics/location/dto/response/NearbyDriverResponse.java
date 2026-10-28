package org.example.ride_hailing_logistics.location.dto.response;

import java.time.Instant;
import java.util.UUID;

public record NearbyDriverResponse(UUID driverId, double distanceMeters, long locationAgeMillis, double accuracyMeters,
                                   Instant lastSeenAt) {}
