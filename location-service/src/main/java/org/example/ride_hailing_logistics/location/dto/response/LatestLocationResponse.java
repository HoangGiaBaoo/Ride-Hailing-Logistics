package org.example.ride_hailing_logistics.location.dto.response;

import java.time.Instant;
import java.util.UUID;

public record LatestLocationResponse(UUID driverId, double latitude, double longitude, double accuracyMeters,
                                     Double heading, Double speed, Instant serverTimestamp) {}
