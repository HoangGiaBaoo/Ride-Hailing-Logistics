package org.example.ride_hailing_logistics.trip.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/** Hop dong ban tin tren topic {ns}.location.updates do location-service phat (khoa = driverId). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DriverLocationUpdated(UUID driverId, double latitude, double longitude, double accuracyMeters,
                                    Double heading, Double speed, Instant deviceTimestamp, Instant serverTimestamp,
                                    boolean suspicious) {}
