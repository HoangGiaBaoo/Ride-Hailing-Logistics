package org.example.ride_hailing_logistics.location.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Vi tri moi nhat cua tai xe trong Redis (DR-GEO-002: kem accuracy, thoi diem, nguon). */
public record LatestLocation(UUID driverId, double latitude, double longitude, double accuracyMeters, Double heading,
                             Double speed, Instant deviceTimestamp, Instant serverTimestamp, long sequence,
                             String vehicleType, boolean suspicious) {

    /** Do moi tinh theo thoi diem server nhan (A-09: tin timestamp server hon timestamp thiet bi). */
    public boolean isFresh(Instant now, Duration freshness) {
        return serverTimestamp != null && !serverTimestamp.isBefore(now.minus(freshness));
    }

    /** Du chat luong de dung cho quyet dinh matching (BR-004, DR-GEO-006). */
    public boolean usableForMatching(Instant now, Duration freshness, double maxAccuracy) {
        return isFresh(now, freshness) && accuracyMeters <= maxAccuracy && !suspicious;
    }
}
