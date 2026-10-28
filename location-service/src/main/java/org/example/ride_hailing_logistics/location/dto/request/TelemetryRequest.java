package org.example.ride_hailing_logistics.location.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

/**
 * Ban tin vi tri tu Driver App (FR-LOC-002, Phu luc A.1).
 * Khong co truong driverId: danh tinh lay tu phien xac thuc, khong tin payload (FR-LOC-005).
 */
public record TelemetryRequest(
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotNull @PositiveOrZero Double accuracyMeters,
        Double headingDegrees,
        Double speedMetersPerSecond,
        @NotNull Instant deviceTimestamp,
        @NotNull @PositiveOrZero Long sequence) {
}
