package org.example.ride_hailing_logistics;

import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.driver.DriverAvailabilityService;
import org.example.ride_hailing_logistics.driver.DriverDtos.UpdateDriverProfileRequest;
import org.example.ride_hailing_logistics.driver.DriverDtos.VehicleRequest;
import org.example.ride_hailing_logistics.driver.DriverEnums.DocumentType;
import org.example.ride_hailing_logistics.driver.DriverEnums.ReviewDecisionType;
import org.example.ride_hailing_logistics.driver.DriverProfileService;
import org.example.ride_hailing_logistics.driver.DriverReviewService;
import org.example.ride_hailing_logistics.identity.AuthService;
import org.example.ride_hailing_logistics.identity.IdentityDtos.RegisterRequest;
import org.example.ride_hailing_logistics.identity.IdentityDtos.SelfRegistrationRole;
import org.example.ride_hailing_logistics.identity.User;
import org.example.ride_hailing_logistics.identity.UserRepository;
import org.example.ride_hailing_logistics.location.LocationService;
import org.example.ride_hailing_logistics.location.Telemetry;
import org.example.ride_hailing_logistics.pricing.PricingRuleService;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.Place;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.PricingRuleRequest;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.QuoteRequest;
import org.example.ride_hailing_logistics.pricing.QuoteService;
import org.example.ride_hailing_logistics.trip.TripDtos.CreateTripRequest;
import org.example.ride_hailing_logistics.trip.TripDtos.TripResponse;
import org.example.ride_hailing_logistics.trip.TripService;
import org.example.ride_hailing_logistics.trip.dispatch.DispatchService;
import org.example.ride_hailing_logistics.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Nen cho integration test: PostgreSQL that (ride_hailing_test) + Redis that (DB so 1).
 * Du lieu dung qua dung service nghiep vu, khong chen thang vao bang, de test chung minh hanh vi that.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    protected static final Place PICKUP = new Place(10.7769, 106.7009, "Nhà thờ Đức Bà");
    protected static final Place DROPOFF = new Place(10.7626, 106.6602, "ĐH Bách Khoa");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final AtomicInteger COUNTER = new AtomicInteger(1);
    private static final AtomicLong SEQUENCE = new AtomicLong(1);

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected StringRedisTemplate redis;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected AuthService authService;
    @Autowired protected DriverProfileService driverProfiles;
    @Autowired protected DriverReviewService driverReview;
    @Autowired protected DriverAvailabilityService driverAvailability;
    @Autowired protected LocationService locationService;
    @Autowired protected PricingRuleService pricingRules;
    @Autowired protected WalletService walletService;
    @Autowired protected QuoteService quoteService;
    @Autowired protected TripService tripService;
    @Autowired protected DispatchService dispatchService;

    protected Actor admin;
    protected Actor reviewer;

    @BeforeEach
    void resetState() {
        // TRUNCATE khong kich hoat trigger chan DELETE theo dong -> xoa duoc ca bang bat bien giua cac test
        jdbc.execute("""
                TRUNCATE outbox_events, processed_events, audit_records, idempotency_records, user_roles, refresh_tokens,
                password_reset_tokens, driver_review_decisions, driver_documents, vehicles, driver_profiles, fare_quotes,
                pricing_rules, trip_status_history, driver_offers, trips, refunds, payments, payment_callback_events,
                wallet_entries, wallets, commission_rules, users CASCADE
                """);
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
        Instant now = Instant.now();
        admin = staff(Role.ADMIN, now);
        reviewer = staff(Role.REVIEWER, now);
        pricingRules.create(new PricingRuleRequest(ServiceType.RIDE, VehicleType.MOTORBIKE, "HCM", bd(10_000), bd(4_500),
                bd(300), bd(15_000), bd(2_000), now, null, "test"), admin);
        walletService.createCommissionRule(ServiceType.RIDE, new BigDecimal("0.20"), now, admin);
    }

    protected UUID newCustomer() {
        int n = COUNTER.getAndIncrement();
        return authService.register(new RegisterRequest("c" + n + "@it.test", phone(2, n), "Test@12345", "Khách " + n,
                SelfRegistrationRole.CUSTOMER)).id();
    }

    /** Tai xe da duoc duyet, Online, co vi tri moi tai (lat, lng). */
    protected UUID onlineDriverAt(double lat, double lng) {
        int n = COUNTER.getAndIncrement();
        UUID id = authService.register(new RegisterRequest("d" + n + "@it.test", phone(3, n), "Test@12345", "Tài xế " + n,
                SelfRegistrationRole.DRIVER)).id();
        LocalDate expiry = LocalDate.now().plusYears(2);
        driverProfiles.updateProfile(id, new UpdateDriverProfileRequest("LIC" + n, "A1", expiry, "0790" + String.format("%08d", n), "HCM"));
        driverProfiles.upsertVehicle(id, new VehicleRequest(VehicleType.MOTORBIKE, "59X" + String.format("%05d", n), "Honda", "Wave", "Đen", 2022));
        for (DocumentType type : DocumentType.values()) {
            driverProfiles.uploadDocument(id, type, type.name() + n, expiry, new MockMultipartFile("file", "doc.png", "image/png", PNG));
        }
        driverProfiles.submit(id);
        driverReview.decide(id, ReviewDecisionType.APPROVE, null, reviewer);
        driverAvailability.goOnline(id);
        sendLocation(id, lat, lng);
        return id;
    }

    protected void sendLocation(UUID driverId, double lat, double lng) {
        locationService.ingest(driverId, new Telemetry(lat, lng, 5.0, null, null, Instant.now(), SEQUENCE.getAndIncrement()));
    }

    protected TripResponse requestRide(UUID customerId) {
        var quote = quoteService.createQuote(customerId, new QuoteRequest(ServiceType.RIDE, VehicleType.MOTORBIKE, PICKUP, DROPOFF));
        return tripService.create(customerId, new CreateTripRequest(quote.quoteId(), PaymentMethod.CASH, true, null, null, null));
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

    private Actor staff(Role role, Instant now) {
        int n = COUNTER.getAndIncrement();
        User u = users.save(User.register(role.name().toLowerCase() + n + "@it.test", "+84" + phone(1, n).substring(1),
                passwordEncoder.encode("Test@12345"), role.name(), role, now));
        return new Actor(u.getId(), Set.of(role));
    }

    private static String phone(int group, int n) {
        return "09" + group + String.format("%07d", n);
    }

    protected static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }
}
