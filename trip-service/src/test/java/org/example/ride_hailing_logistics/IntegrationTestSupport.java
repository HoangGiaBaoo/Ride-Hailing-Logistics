package org.example.ride_hailing_logistics;

import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.trip.TripDtos.CreateTripRequest;
import org.example.ride_hailing_logistics.trip.TripDtos.TripResponse;
import org.example.ride_hailing_logistics.trip.TripService;
import org.example.ride_hailing_logistics.trip.dispatch.DispatchService;
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

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Nen cho integration test cua trip-service: PostgreSQL that (rhl_trip_test) + Redis that (DB so 1).
 * driver-service va pricing-service la phu thuoc ngoai cua trip-service -> thay bang ban gia trong bo nho
 * co cung ngu nghia nguyen tu; ban that cua chung duoc kiem o integration test cua chinh service do.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestSupport.Doubles.class)
public abstract class IntegrationTestSupport {

    protected static final double PICKUP_LAT = 10.7769;
    protected static final double PICKUP_LNG = 106.7009;
    protected static final double DROPOFF_LAT = 10.7626;
    protected static final double DROPOFF_LNG = 106.6602;

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected StringRedisTemplate redis;
    @Autowired protected TripService tripService;
    @Autowired protected DispatchService dispatchService;
    @Autowired protected InMemoryDriverPool drivers;
    @Autowired protected InMemoryPricing pricing;

    @TestConfiguration
    static class Doubles {

        @Bean
        @Primary
        InMemoryDriverPool inMemoryDriverPool() {
            return new InMemoryDriverPool();
        }

        @Bean
        @Primary
        InMemoryPricing inMemoryPricing() {
            return new InMemoryPricing();
        }
    }

    @BeforeEach
    void resetState() {
        // TRUNCATE khong kich hoat trigger chan DELETE theo dong -> xoa duoc ca bang bat bien giua cac test
        // Scheduler dispatch/outbox van chay giua cac test: TRUNCATE (khoa AccessExclusive) co the thanh nan nhan deadlock
        for (int attempt = 1; ; attempt++) {
            try {
                jdbc.execute("""
                        TRUNCATE outbox_events, inbox_events, processed_events, audit_records, idempotency_records,
                        trip_status_history, driver_offers, trips CASCADE
                        """);
                break;
            } catch (PessimisticLockingFailureException e) {
                if (attempt == 5) throw e;
            }
        }
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
        drivers.reset();
        pricing.reset();
    }

    protected UUID newCustomer() {
        return UUID.randomUUID();   // danh tinh den tu token cua identity-service; trip-service chi luu ID
    }

    /** Tai xe AVAILABLE co vi tri moi tai (lat, lng) theo driver-service gia. */
    protected UUID onlineDriverAt(double lat, double lng) {
        UUID id = UUID.randomUUID();
        drivers.addAvailable(id, lat, lng);
        return id;
    }

    protected CreateTripRequest rideRequest(UUID customerId) {
        UUID quoteId = pricing.issue(customerId, PICKUP_LAT, PICKUP_LNG, DROPOFF_LAT, DROPOFF_LNG);
        return new CreateTripRequest(quoteId, PaymentMethod.CASH, true, null, null, null);
    }

    protected TripResponse requestRide(UUID customerId) {
        return tripService.create(customerId, rideRequest(customerId));
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
