package org.example.ride_hailing_logistics.location.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.util.GeoUtils;
import org.example.ride_hailing_logistics.location.domain.TelemetryValidator;
import org.example.ride_hailing_logistics.location.dto.request.TelemetryRequest;
import org.example.ride_hailing_logistics.location.dto.response.LatestLocationResponse;
import org.example.ride_hailing_logistics.location.dto.response.NearbyDriverResponse;
import org.example.ride_hailing_logistics.location.dto.response.SupplyResponse;
import org.example.ride_hailing_logistics.location.dto.response.TelemetryAckResponse;
import org.example.ride_hailing_logistics.location.entity.LatestLocation;
import org.example.ride_hailing_logistics.location.enums.DriverAvailability;
import org.example.ride_hailing_logistics.location.event.DriverAvailabilityChanged;
import org.example.ride_hailing_logistics.location.event.DriverLocationUpdated;
import org.example.ride_hailing_logistics.location.event.LocationEventPublisher;
import org.example.ride_hailing_logistics.location.exception.DriverNotActiveException;
import org.example.ride_hailing_logistics.location.exception.InvalidTelemetryException;
import org.example.ride_hailing_logistics.location.mapper.LocationMapper;
import org.example.ride_hailing_logistics.location.repository.DriverContextRepository;
import org.example.ride_hailing_logistics.location.repository.DriverLocationIndex;
import org.example.ride_hailing_logistics.location.service.LocationService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class LocationServiceImpl implements LocationService {

    public static final String CONTEXT_CACHE = "driver-context";

    private final DriverLocationIndex index;
    private final DriverContextRepository contexts;
    private final LocationEventPublisher publisher;
    private final LocationMapper mapper;
    private final Cache contextCache;
    private final TelemetryValidator validator;
    private final AppProperties.Location props;
    private final Clock clock;

    public LocationServiceImpl(DriverLocationIndex index, DriverContextRepository contexts,
                               LocationEventPublisher publisher, LocationMapper mapper, CacheManager cacheManager,
                               AppProperties appProperties, Clock clock) {
        this.index = index;
        this.contexts = contexts;
        this.publisher = publisher;
        this.mapper = mapper;
        // ~33 ban tin/giay: doc ngu canh tu cache Redis thay vi moi ban tin mot truy van DB
        this.contextCache = cacheManager.getCache(CONTEXT_CACHE);
        this.props = appProperties.location();
        this.clock = clock;
        this.validator = new TelemetryValidator(props.maxAccuracyMeters(), props.maxFutureSkew(), props.maxAge(),
                props.freshness(), props.maxSpeedMps());
    }

    @Override
    public TelemetryAckResponse ingest(UUID driverId, TelemetryRequest telemetry) {
        Instant now = clock.instant();
        ContextView ctx = context(driverId);
        if (ctx == null || ctx.availability() == DriverAvailability.OFFLINE) {
            throw new DriverNotActiveException();
        }
        LatestLocation previous = index.get(driverId).orElse(null);
        TelemetryValidator.Result result = validator.validate(telemetry, previous, now);
        switch (result.outcome()) {
            case REJECTED -> throw new InvalidTelemetryException(result.reason());
            case HISTORICAL -> {
                // Gui bu sau mat mang: khong dung lam vi tri hien tai (FR-LOC-013)
                return new TelemetryAckResponse(false, true, false, props.recommendedInterval().toSeconds());
            }
            default -> {
            }
        }
        boolean written = index.upsert(driverId, ctx.vehicleType().name(), telemetry, result.suspicious(), now);
        if (written) {
            publisher.publish(new DriverLocationUpdated(driverId, telemetry.latitude(), telemetry.longitude(),
                    telemetry.accuracyMeters(), telemetry.headingDegrees(), telemetry.speedMetersPerSecond(),
                    telemetry.deviceTimestamp(), now, result.suspicious()));
        }
        return new TelemetryAckResponse(written, false, result.suspicious(), props.recommendedInterval().toSeconds());
    }

    @Override
    public List<NearbyDriverResponse> findNearby(double lat, double lng, double radiusMeters, VehicleType vehicleType,
                                                 int limit) {
        Instant now = clock.instant();
        List<NearbyDriverResponse> result = new ArrayList<>();
        for (DriverLocationIndex.Hit hit : index.search(lat, lng, radiusMeters, vehicleType.name(), limit * 2)) {
            index.get(hit.driverId())
                    .filter(l -> l.usableForMatching(now, props.freshness(), props.maxAccuracyForMatchingMeters()))
                    .ifPresent(l -> result.add(new NearbyDriverResponse(hit.driverId(), hit.distanceMeters(),
                            Duration.between(l.serverTimestamp(), now).toMillis(), l.accuracyMeters(), l.serverTimestamp())));
            if (result.size() >= limit) break;
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public SupplyResponse supply(String cell) {
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
        long available = ids.isEmpty() ? 0 : contexts.countByDriverIdInAndAvailability(ids, DriverAvailability.AVAILABLE);
        return new SupplyResponse(cell, available);
    }

    @Override
    public Optional<LatestLocationResponse> latest(UUID driverId) {
        return index.get(driverId).map(mapper::toResponse);
    }

    @Override
    @Transactional
    public void applyAvailability(DriverAvailabilityChanged change) {
        int updated = contexts.upsertIfNewer(change.driverId(), change.newStatus().name(), change.vehicleType().name(),
                change.version(), clock.instant());
        if (updated == 0) {
            return;   // ban tin cu hon trang thai da luu
        }
        contextCache.evict(change.driverId());
        if (change.newStatus() == DriverAvailability.OFFLINE) {
            index.remove(change.driverId());
        }
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

    private ContextView context(UUID driverId) {
        ContextView cached = contextCache.get(driverId, ContextView.class);
        if (cached != null) return cached;
        ContextView fresh = contexts.findById(driverId)
                .map(c -> new ContextView(c.getAvailability(), c.getVehicleType()))
                .orElse(null);
        if (fresh != null && fresh.availability() != DriverAvailability.OFFLINE) {
            contextCache.put(driverId, fresh);
        }
        return fresh;
    }

    /** Gia tri cache: chi hai truong can cho moi ban tin. */
    public record ContextView(DriverAvailability availability, VehicleType vehicleType) {}
}
