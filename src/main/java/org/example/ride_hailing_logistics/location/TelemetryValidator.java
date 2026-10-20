package org.example.ride_hailing_logistics.location;

import org.example.ride_hailing_logistics.common.util.GeoUtils;

import java.time.Duration;
import java.time.Instant;

/**
 * Kiem tra ban tin telemetry (FR-LOC-003, FR-LOC-004, FR-LOC-013). Thuan, khong Spring, test duoc.
 *
 * Ket qua:
 * - REJECTED: sai dinh dang / pham vi / accuracy qua kem / thoi diem bat thuong -> bao lai cho app
 * - HISTORICAL: ban tin gui bu sau mat mang, da cu hon nguong do moi -> ghi nhan nhung KHONG cap nhat vi tri hien tai
 * - ACCEPTED: dung lam vi tri hien tai; co the kem co suspicious neu nhay vi tri bat kha thi
 */
public final class TelemetryValidator {

    private final double maxAccuracyMeters;
    private final Duration maxFutureSkew;
    private final Duration maxAge;
    private final Duration freshness;
    private final double maxSpeedMps;

    public TelemetryValidator(double maxAccuracyMeters, Duration maxFutureSkew, Duration maxAge, Duration freshness,
                              double maxSpeedMps) {
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.maxFutureSkew = maxFutureSkew;
        this.maxAge = maxAge;
        this.freshness = freshness;
        this.maxSpeedMps = maxSpeedMps;
    }

    public Result validate(Telemetry t, LatestLocation previous, Instant serverNow) {
        // Kenh WebSocket khong chay Bean Validation nhu REST: tu kiem null o day
        if (t == null || t.latitude() == null || t.longitude() == null || t.sequence() == null) {
            return Result.rejected("Bản tin thiếu tọa độ hoặc sequence.");
        }
        if (!GeoUtils.isValidCoordinate(t.latitude(), t.longitude())) {
            return Result.rejected("Tọa độ ngoài phạm vi hợp lệ.");
        }
        if (t.accuracyMeters() == null || t.accuracyMeters() < 0 || t.accuracyMeters().isNaN()) {
            return Result.rejected("Thiếu hoặc sai độ chính xác GPS.");
        }
        if (t.accuracyMeters() > maxAccuracyMeters) {
            return Result.rejected("Độ chính xác GPS quá kém (" + Math.round(t.accuracyMeters()) + " m).");
        }
        if (t.sequence() < 0) {
            return Result.rejected("Sequence không hợp lệ.");
        }
        if (t.deviceTimestamp() == null) {
            return Result.rejected("Thiếu thời điểm thiết bị.");
        }
        if (t.deviceTimestamp().isAfter(serverNow.plus(maxFutureSkew))) {
            return Result.rejected("Thời điểm thiết bị ở tương lai bất thường.");
        }
        if (t.deviceTimestamp().isBefore(serverNow.minus(maxAge))) {
            return Result.rejected("Bản tin quá cũ.");
        }
        if (t.deviceTimestamp().isBefore(serverNow.minus(freshness))) {
            return Result.historical();
        }
        boolean suspicious = false;
        if (previous != null && previous.deviceTimestamp() != null) {
            double seconds = Duration.between(previous.deviceTimestamp(), t.deviceTimestamp()).toMillis() / 1000.0;
            double meters = GeoUtils.haversineMeters(previous.latitude(), previous.longitude(), t.latitude(), t.longitude());
            // Nhay xa trong thoi gian ngan hon kha nang di chuyen thuc te -> danh dau nghi van (NFR-PRV-006)
            if (seconds > 0 && meters / seconds > maxSpeedMps) {
                suspicious = true;
            }
            if (seconds <= 0 && meters > 200) {
                suspicious = true;
            }
        }
        return Result.accepted(suspicious);
    }

    public enum Outcome { ACCEPTED, HISTORICAL, REJECTED }

    public record Result(Outcome outcome, boolean suspicious, String reason) {
        static Result accepted(boolean suspicious) {
            return new Result(Outcome.ACCEPTED, suspicious, null);
        }

        static Result historical() {
            return new Result(Outcome.HISTORICAL, false, null);
        }

        static Result rejected(String reason) {
            return new Result(Outcome.REJECTED, false, reason);
        }
    }
}
