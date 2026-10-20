package org.example.ride_hailing_logistics.location;

import java.time.Instant;
import java.util.UUID;

/**
 * Event trong tien trinh (khong qua outbox): vi tri la du lieu song, chi can giao nhanh cho realtime (NFR-PERF-001).
 * Mat mot ban tin khong sao vi 3 giay sau co ban moi.
 */
public record DriverLocationUpdated(UUID driverId, double latitude, double longitude, double accuracyMeters,
                                    Double heading, Double speed, Instant deviceTimestamp, Instant serverTimestamp,
                                    boolean suspicious) {
}
