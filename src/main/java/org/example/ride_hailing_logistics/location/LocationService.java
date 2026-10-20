package org.example.ride_hailing_logistics.location;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.util.GeoUtils;
import org.example.ride_hailing_logistics.driver.DriverAvailabilityService;
import org.example.ride_hailing_logistics.driver.DriverAvailabilityService.TelemetryContext;
import org.example.ride_hailing_logistics.driver.DriverEnums.DriverAvailability;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LocationService {

    // Cache ngu canh tai xe de 33 ban tin/giay khong thanh 33 truy van DB/giay; khong cache trang thai OFFLINE
    private static final Duration CONTEXT_TTL = Duration.ofSeconds(15);

    private final DriverLocationIndex index;
    private final DriverAvailabilityService driverAvailability;
    private final ApplicationEventPublisher events;
    private final TelemetryValidator validator;
    private final AppProperties.Location props;
    private final Clock clock;
    private final Map<UUID, CachedContext> contextCache = new ConcurrentHashMap<>();

    public LocationService(DriverLocationIndex index, DriverAvailabilityService driverAvailability,
                           ApplicationEventPublisher events, AppProperties appProperties, Clock clock) {
        this.index = index;
        this.driverAvailability = driverAvailability;
        this.events = events;
        this.props = appProperties.location();
        this.clock = clock;
        this.validator = new TelemetryValidator(props.maxAccuracyMeters(), props.maxFutureSkew(), props.maxAge(),
                props.freshness(), props.maxSpeedMps());
    }

    /** Nhan telemetry tu REST hoac WebSocket. driverId luon lay tu phien da xac thuc (FR-LOC-005). */
    public TelemetryAck ingest(UUID driverId, Telemetry telemetry) {
        Instant now = clock.instant();
        TelemetryContext ctx = context(driverId);
        if (ctx.availability() == DriverAvailability.OFFLINE) {
            throw BusinessException.rule("Bạn đang Offline, hãy bật Online trước khi gửi vị trí.");
        }
        LatestLocation previous = index.get(driverId).orElse(null);
        TelemetryValidator.Result result = validator.validate(telemetry, previous, now);
        switch (result.outcome()) {
            case REJECTED -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, result.reason());
            case HISTORICAL -> {
                // Gui bu sau mat mang: khong dung lam vi tri hien tai (FR-LOC-013)
                return new TelemetryAck(false, true, false, props.recommendedInterval().toSeconds());
            }
            default -> {
            }
        }
        boolean written = index.upsert(driverId, ctx.vehicleType().name(), telemetry, result.suspicious(), now);
        if (written) {
            events.publishEvent(new DriverLocationUpdated(driverId, telemetry.latitude(), telemetry.longitude(),
                    telemetry.accuracyMeters(), telemetry.headingDegrees(), telemetry.speedMetersPerSecond(),
                    telemetry.deviceTimestamp(), now, result.suspicious()));
        }
        return new TelemetryAck(written, false, result.suspicious(), props.recommendedInterval().toSeconds());
    }

    /**
     * Ung vien gan diem don, da loc do moi va chat luong vi tri (FR-LOC-009, FR-LOC-010, BR-004).
     * Chua loc trang thai kha dung — viec do cua mien driver.
     */
    public List<NearbyDriver> findNearby(double lat, double lng, double radiusMeters, VehicleType vehicleType, int limit) {
        Instant now = clock.instant();
        List<NearbyDriver> result = new ArrayList<>();
        for (DriverLocationIndex.Hit hit : index.search(lat, lng, radiusMeters, vehicleType.name(), limit * 2)) {
            index.get(hit.driverId())
                    .filter(l -> l.usableForMatching(now, props.freshness(), props.maxAccuracyForMatchingMeters()))
                    .ifPresent(l -> result.add(new NearbyDriver(hit.driverId(), hit.distanceMeters(),
                            Duration.between(l.serverTimestamp(), now).toMillis(), l.accuracyMeters(), l.serverTimestamp())));
            if (result.size() >= limit) break;
        }
        return result;
    }

    /** Tai xe co vi tri moi nam trong o geohash (moi loai xe) — dau vao nguon cung cho surge (FR-PRI-008). */
    public List<UUID> freshDriversInCell(String cell) {
        double[] center = GeoUtils.geohashCenterAndRadius(cell);
        Instant now = clock.instant();
        List<UUID> ids = new ArrayList<>();
        for (VehicleType type : VehicleType.values()) {
            for (DriverLocationIndex.Hit hit : index.search(center[0], center[1], center[2], type.name(), 500)) {
                index.get(hit.driverId())
                        .filter(l -> l.isFresh(now, props.freshness()))
                        .filter(l -> GeoUtils.geohash(l.latitude(), l.longitude(), cell.length()).equals(cell))
                        .ifPresent(l -> ids.add(l.driverId()));
            }
        }
        return ids;
    }

    public Optional<LatestLocation> latest(UUID driverId) {
        return index.get(driverId);
    }

    /** Offline / dinh chi -> xoa khoi chi muc ngay (FR-LOC-008). */
    public void removeDriver(UUID driverId) {
        contextCache.remove(driverId);
        index.remove(driverId);
    }

    @Scheduled(fixedDelay = 10_000)
    public void purgeStale() {
        try {
            int removed = index.removeStale(clock.instant().minus(props.maxAge()), 1000);
            if (removed > 0) {
                log.info("Removed {} stale driver locations", removed);
            }
        } catch (RuntimeException e) {
            log.warn("Stale location purge failed: {}", e.getMessage());
        }
    }

    private TelemetryContext context(UUID driverId) {
        Instant now = clock.instant();
        CachedContext cached = contextCache.get(driverId);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.context();
        }
        TelemetryContext ctx = driverAvailability.telemetryContext(driverId)
                .orElseThrow(() -> BusinessException.rule("Tài xế chưa có phương tiện đang hoạt động."));
        if (ctx.availability() != DriverAvailability.OFFLINE) {
            contextCache.put(driverId, new CachedContext(ctx, now.plus(CONTEXT_TTL)));
        } else {
            contextCache.remove(driverId);
        }
        return ctx;
    }

    private record CachedContext(TelemetryContext context, Instant expiresAt) {}

    public record TelemetryAck(boolean accepted, boolean historical, boolean suspicious, long nextIntervalSeconds) {}

    public record NearbyDriver(UUID driverId, double distanceMeters, long locationAgeMillis, double accuracyMeters,
                               Instant lastSeenAt) {}
}
