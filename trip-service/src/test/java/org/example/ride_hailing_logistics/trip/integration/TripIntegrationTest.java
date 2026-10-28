package org.example.ride_hailing_logistics.trip.integration;

import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.util.GeoUtils;
import org.example.ride_hailing_logistics.trip.client.DriverClient;
import org.example.ride_hailing_logistics.trip.client.LocationClient;
import org.example.ride_hailing_logistics.trip.client.PricingClient;
import org.example.ride_hailing_logistics.trip.client.dto.ConsumeQuoteRequest;
import org.example.ride_hailing_logistics.trip.client.dto.DriverLocationResponse;
import org.example.ride_hailing_logistics.trip.client.dto.DriverSnapshotResponse;
import org.example.ride_hailing_logistics.trip.client.dto.NearbyDriverResponse;
import org.example.ride_hailing_logistics.trip.client.dto.OnlineEligibilityResponse;
import org.example.ride_hailing_logistics.trip.client.dto.QuoteSnapshotResponse;
import org.example.ride_hailing_logistics.trip.client.dto.ReleaseQuoteRequest;
import org.example.ride_hailing_logistics.trip.client.dto.TelemetryAckResponse;
import org.example.ride_hailing_logistics.trip.dto.request.CreateTripRequest;
import org.example.ride_hailing_logistics.trip.dto.request.TelemetryRequest;
import org.example.ride_hailing_logistics.trip.dto.response.TripResponse;
import org.example.ride_hailing_logistics.trip.enums.DriverAvailability;
import org.example.ride_hailing_logistics.trip.service.DispatchService;
import org.example.ride_hailing_logistics.trip.service.DriverStateService;
import org.example.ride_hailing_logistics.trip.service.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Nen integration test cua trip-service: PostgreSQL that (rhl_trip_test), Redis that (DB 1), Kafka that (namespace
 * rhltest). Code cua trip-service chay that tu dau den cuoi; chi ba service khac (pricing, location, driver) — phu
 * thuoc ngoai cua trip-service — duoc thay bang ban gia trong bo nho, giu dung hop dong cua API /internal.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Import(TripIntegrationTest.Doubles.class)
public abstract class TripIntegrationTest {

    protected static final double PICKUP_LAT = 10.7769;
    protected static final double PICKUP_LNG = 106.7009;
    /** 12.000 + 4,2 km x 5.000 + 14 phut x 500 = 40.000 (so co dinh, pricing that da duoc test o pricing-service). */
    protected static final BigDecimal QUOTED_TOTAL = new BigDecimal("40000");
    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected StringRedisTemplate redis;
    @Autowired protected FakePricing pricing;
    @Autowired protected FakeLocation location;
    @Autowired protected FakeDriverDirectory drivers;
    @Autowired protected DriverStateService driverStates;
    @Autowired protected TripService tripService;
    @Autowired protected DispatchService dispatchService;

    @TestConfiguration
    static class Doubles {

        @Bean
        @Primary
        FakePricing fakePricing() {
            return new FakePricing();
        }

        @Bean
        @Primary
        FakeLocation fakeLocation() {
            return new FakeLocation();
        }

        @Bean
        @Primary
        FakeDriverDirectory fakeDriverDirectory() {
            return new FakeDriverDirectory();
        }
    }

    /** Hop dong /internal/quotes: quote cua nguoi khac -> 404; tieu lai cung tripId -> cung snapshot. */
    public static class FakePricing implements PricingClient {

        private final Map<UUID, QuoteSnapshotResponse> quotes = new ConcurrentHashMap<>();
        private final Map<UUID, UUID> consumedBy = new ConcurrentHashMap<>();

        public UUID issue(UUID customerId) {
            UUID id = UUID.randomUUID();
            quotes.put(id, new QuoteSnapshotResponse(id, customerId, ServiceType.RIDE, VehicleType.MOTORBIKE, PICKUP_LAT,
                    PICKUP_LNG, "Nhà thờ Đức Bà", 10.7626, 106.6602, "ĐH Bách Khoa", 4200, 840, new BigDecimal("12000"),
                    new BigDecimal("21000"), new BigDecimal("7000"), BigDecimal.ZERO, BigDecimal.ONE, QUOTED_TOTAL, "VND",
                    UUID.randomUUID(), 1));
            return id;
        }

        public int consumedCount() {
            return consumedBy.size();
        }

        @Override
        public synchronized ApiResponse<QuoteSnapshotResponse> consume(UUID quoteId, ConsumeQuoteRequest request) {
            QuoteSnapshotResponse quote = quotes.get(quoteId);
            if (quote == null || !quote.customerId().equals(request.customerId())) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy báo giá.");
            }
            UUID owner = consumedBy.putIfAbsent(quoteId, request.tripId());
            if (owner != null && !owner.equals(request.tripId())) {
                throw new BusinessException(ErrorCode.QUOTE_EXPIRED, "Báo giá đã được sử dụng.");
            }
            return ApiResponse.ok(quote);
        }

        @Override
        public synchronized ApiResponse<Void> release(UUID quoteId, ReleaseQuoteRequest request) {
            consumedBy.remove(quoteId, request.tripId());
            return ApiResponse.ok(null);
        }

        void clear() {
            quotes.clear();
            consumedBy.clear();
        }
    }

    /** Hop dong /internal/locations: tim tai xe trong ban kinh theo haversine, gan nhat truoc. */
    public static class FakeLocation implements LocationClient {

        private final Map<UUID, double[]> positions = new ConcurrentHashMap<>();

        public void put(UUID driverId, double lat, double lng) {
            positions.put(driverId, new double[]{lat, lng});
        }

        @Override
        public ApiResponse<List<NearbyDriverResponse>> nearby(double lat, double lng, double radiusMeters,
                                                              VehicleType vehicleType, int limit) {
            Instant now = Instant.now();
            return ApiResponse.ok(positions.entrySet().stream()
                    .map(e -> new NearbyDriverResponse(e.getKey(),
                            GeoUtils.haversineMeters(lat, lng, e.getValue()[0], e.getValue()[1]), 0, 5.0, now))
                    .filter(n -> n.distanceMeters() <= radiusMeters)
                    .sorted(Comparator.comparingDouble(NearbyDriverResponse::distanceMeters))
                    .limit(limit)
                    .toList());
        }

        @Override
        public ApiResponse<DriverLocationResponse> latest(UUID driverId) {
            double[] p = positions.get(driverId);
            if (p == null) throw BusinessException.notFound("vị trí tài xế");
            return ApiResponse.ok(new DriverLocationResponse(driverId, p[0], p[1], 5.0, null, null, Instant.now()));
        }

        @Override
        public ApiResponse<TelemetryAckResponse> ingest(UUID driverId, TelemetryRequest telemetry) {
            put(driverId, telemetry.latitude(), telemetry.longitude());
            return ApiResponse.ok(new TelemetryAckResponse(true, false, false, 3));
        }

        void clear() {
            positions.clear();
        }
    }

    /** Hop dong /internal/drivers/{id}/online-eligibility: tai xe da duoc duyet, du giay to. */
    public static class FakeDriverDirectory implements DriverClient {

        private final Map<UUID, DriverSnapshotResponse> approved = new ConcurrentHashMap<>();

        public void approve(UUID driverId, String name, String phone, String plate) {
            approved.put(driverId, new DriverSnapshotResponse(driverId, name, phone, UUID.randomUUID(),
                    VehicleType.MOTORBIKE, plate, "Honda Wave - Đen"));
        }

        @Override
        public ApiResponse<OnlineEligibilityResponse> onlineEligibility(UUID driverId) {
            DriverSnapshotResponse snapshot = approved.get(driverId);
            return ApiResponse.ok(snapshot == null
                    ? new OnlineEligibilityResponse(false, List.of("Hồ sơ chưa được duyệt."), null)
                    : new OnlineEligibilityResponse(true, List.of(), snapshot));
        }

        void clear() {
            approved.clear();
        }
    }

    @BeforeEach
    void resetState() {
        // TRUNCATE khong kich hoat trigger chan DELETE theo dong -> xoa duoc ca bang bat bien giua cac test.
        // Scheduler dispatch/outbox van chay giua cac test: TRUNCATE (khoa AccessExclusive) co the thanh nan nhan deadlock
        for (int attempt = 1; ; attempt++) {
            try {
                jdbc.execute("""
                        TRUNCATE outbox_events, inbox_events, processed_events, audit_records, idempotency_records,
                        trip_status_history, driver_offers, trips, driver_states CASCADE
                        """);
                break;
            } catch (PessimisticLockingFailureException e) {
                if (attempt == 5) throw e;
            }
        }
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
        pricing.clear();
        location.clear();
        drivers.clear();
    }

    protected UUID newCustomer() {
        return UUID.randomUUID();
    }

    /** Tai xe da duoc duyet (o driver-service), bam Online qua luong that cua trip-service, co vi tri tai (lat, lng). */
    protected UUID onlineDriverAt(double lat, double lng) {
        int n = COUNTER.getAndIncrement();
        UUID id = UUID.randomUUID();
        drivers.approve(id, "Tài xế " + n, "093" + String.format("%07d", n), "59X" + String.format("%05d", n));
        driverStates.goOnline(id);
        location.put(id, lat, lng);
        return id;
    }

    protected CreateTripRequest rideRequest(UUID quoteId) {
        return new CreateTripRequest(quoteId, PaymentMethod.CASH, true, null, null, null);
    }

    protected TripResponse requestRide(UUID customerId) {
        return tripService.create(customerId, "key-" + UUID.randomUUID(), rideRequest(pricing.issue(customerId)));
    }

    protected DriverAvailability availabilityOf(UUID driverId) {
        return DriverAvailability.valueOf(jdbc.queryForObject(
                "select availability from driver_states where driver_id = ?", String.class, driverId));
    }

    protected static <T> T await(Supplier<T> probe, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            T value = probe.get();
            if (value != null) return value;
            Thread.sleep(100);
        }
        return null;
    }

    protected int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }
}
