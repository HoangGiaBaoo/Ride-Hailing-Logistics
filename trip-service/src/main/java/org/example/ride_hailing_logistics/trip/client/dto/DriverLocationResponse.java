package org.example.ride_hailing_logistics.trip.client.dto;

import java.time.Instant;
import java.util.UUID;

public record DriverLocationResponse(UUID driverId, double latitude, double longitude, double accuracyMeters,
                                     Double heading, Double speed, Instant serverTimestamp) {}
