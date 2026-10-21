package org.example.ride_hailing_logistics.trip;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.util.Secrets;
import org.example.ride_hailing_logistics.trip.client.DriverSnapshot;
import org.example.ride_hailing_logistics.trip.client.QuoteSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate chuyen. Trang thai chi doi qua transitionTo(...) — noi duy nhat goi TripStateMachine
 * va ghi trip_status_history (FR-TRIP-008...010). Ghi dong thoi duoc bao ve boi khoa hang + @Version.
 */
@Entity
@Table(name = "trips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip {

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 20)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status;

    @Column(name = "pickup_lat", nullable = false)
    private double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private double pickupLng;

    @Column(name = "pickup_address", nullable = false, length = 255)
    private String pickupAddress;

    @Column(name = "dropoff_lat", nullable = false)
    private double dropoffLat;

    @Column(name = "dropoff_lng", nullable = false)
    private double dropoffLng;

    @Column(name = "dropoff_address", nullable = false, length = 255)
    private String dropoffAddress;

    // ---- snapshot bao gia (BR-007, DR-004): nguon gia doi thi lich su chuyen van khong doi ----
    @Column(name = "quote_id", nullable = false)
    private UUID quoteId;

    @Column(name = "distance_meters", nullable = false)
    private int distanceMeters;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "base_fare", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "distance_fare", nullable = false, precision = 15, scale = 2)
    private BigDecimal distanceFare;

    @Column(name = "time_fare", nullable = false, precision = 15, scale = 2)
    private BigDecimal timeFare;

    @Column(name = "booking_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal bookingFee;

    @Column(name = "surge_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal surgeMultiplier;

    @Column(name = "quoted_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal quotedTotal;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "pricing_rule_id", nullable = false)
    private UUID pricingRuleId;

    @Column(name = "pricing_rule_version", nullable = false)
    private int pricingRuleVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    // ---- ma xac nhan: chi khach thay (FR-TRIP-012, FR-TRIP-013) ----
    @Column(name = "start_pin", nullable = false, length = 4)
    private String startPin;

    @Column(name = "delivery_code", length = 6)
    private String deliveryCode;

    // ---- giao hang: snapshot nguoi nhan & kien hang toi thieu can thiet (FR-TRIP-005) ----
    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "package_description", length = 500)
    private String packageDescription;

    @Column(name = "package_weight_kg")
    private Double packageWeightKg;

    // ---- snapshot tai xe luc ghep (UI-CUS-005) ----
    @Column(name = "driver_name", length = 100)
    private String driverName;

    @Column(name = "driver_phone", length = 20)
    private String driverPhone;

    @Column(name = "vehicle_plate", length = 15)
    private String vehiclePlate;

    @Column(name = "vehicle_description", length = 150)
    private String vehicleDescription;

    // ---- dispatch ----
    @Column(name = "matching_started_at")
    private Instant matchingStartedAt;

    @Column(name = "matching_round", nullable = false)
    private int matchingRound;

    @Column(name = "next_dispatch_at")
    private Instant nextDispatchAt;

    // ---- moc thoi gian ----
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    // ---- huy ----
    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by_role", length = 30)
    private TripActorRole cancelledByRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason_code", length = 30)
    private CancelReason cancelReasonCode;

    @Column(name = "cancel_note", length = 500)
    private String cancelNote;

    @Column(name = "cancellation_fee", precision = 15, scale = 2)
    private BigDecimal cancellationFee;

    @Column(name = "final_fare", precision = 15, scale = 2)
    private BigDecimal finalFare;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    // Wrapper (khong phai long): version null = entity moi -> Spring Data goi persist() chu khong merge()
    private Long version;

    @OneToMany(mappedBy = "trip", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @OrderBy("id")
    private List<TripStatusHistory> history = new ArrayList<>();

    // id sinh truoc: pricing-service ghi quote da dung cho chuyen nao, de goi lai/bu tru dung chuyen
    public static Trip create(UUID id, QuoteSnapshot quote, PaymentMethod paymentMethod, DeliveryDetails delivery, Instant now) {
        Trip t = new Trip();
        t.id = id;
        // Ma hien thi khong mang PII, khong doan duoc thu tu (DR-006)
        t.code = "T" + CODE_DATE.format(now) + Secrets.randomCode(6);
        t.customerId = quote.customerId();
        t.serviceType = quote.serviceType();
        t.vehicleType = quote.vehicleType();
        t.pickupLat = quote.pickupLat();
        t.pickupLng = quote.pickupLng();
        t.pickupAddress = quote.pickupAddress();
        t.dropoffLat = quote.dropoffLat();
        t.dropoffLng = quote.dropoffLng();
        t.dropoffAddress = quote.dropoffAddress();
        t.quoteId = quote.quoteId();
        t.distanceMeters = quote.distanceMeters();
        t.durationSeconds = quote.durationSeconds();
        t.baseFare = quote.baseFare();
        t.distanceFare = quote.distanceFare();
        t.timeFare = quote.timeFare();
        t.bookingFee = quote.bookingFee();
        t.surgeMultiplier = quote.surgeMultiplier();
        t.quotedTotal = quote.total();
        t.currency = quote.currency();
        t.pricingRuleId = quote.ruleId();
        t.pricingRuleVersion = quote.ruleVersion();
        t.paymentMethod = paymentMethod;
        t.startPin = Secrets.randomDigits(4);
        if (delivery != null) {
            t.deliveryCode = Secrets.randomDigits(6);
            t.recipientName = delivery.recipientName();
            t.recipientPhone = delivery.recipientPhone();
            t.packageDescription = delivery.packageDescription();
            t.packageWeightKg = delivery.packageWeightKg();
        }
        t.status = TripStatus.CREATED;
        t.createdAt = now;
        t.updatedAt = now;
        t.history.add(TripStatusHistory.of(t, null, TripStatus.CREATED, quote.customerId(), TripActorRole.CUSTOMER,
                null, null, null, now));
        return t;
    }

    /** Chuyen trang thai co kiem tra + ghi lich su. Moi thay doi trang thai di qua day. */
    void transitionTo(TripStatus to, UUID actorId, TripActorRole role, String reason, Double lat, Double lng, Instant now) {
        TripStateMachine.assertAllowed(status, to, role);
        history.add(TripStatusHistory.of(this, status, to, actorId, role, reason, lat, lng, now));
        status = to;
        updatedAt = now;
        if (to.isTerminal()) {
            endedAt = now;
            nextDispatchAt = null;
        }
    }

    public void startMatching(Instant now) {
        transitionTo(TripStatus.MATCHING, null, TripActorRole.SYSTEM, null, null, null, now);
        matchingStartedAt = now;
        matchingRound = 0;
        nextDispatchAt = now;
    }

    public void advanceMatchingRound() {
        matchingRound++;
    }

    public void scheduleNextDispatch(Instant at) {
        nextDispatchAt = at;
    }

    public void markNoDriver(Instant now) {
        transitionTo(TripStatus.NO_DRIVER, null, TripActorRole.SYSTEM, "Hết thời gian tìm tài xế", null, null, now);
    }

    public void assignDriver(DriverSnapshot driver, Instant now) {
        transitionTo(TripStatus.ACCEPTED, driver.driverId(), TripActorRole.DRIVER, null, null, null, now);
        driverId = driver.driverId();
        vehicleId = driver.vehicleId();
        driverName = driver.fullName();
        driverPhone = driver.phone();
        vehiclePlate = driver.plateNumber();
        vehicleDescription = driver.vehicleDescription();
        acceptedAt = now;
        nextDispatchAt = null;
    }

    public void startPickup(UUID actorId, Double lat, Double lng, Instant now) {
        transitionTo(TripStatus.PICKING_UP, actorId, TripActorRole.DRIVER, null, lat, lng, now);
    }

    public void arrive(UUID actorId, Double lat, Double lng, Instant now) {
        transitionTo(TripStatus.ARRIVED, actorId, TripActorRole.DRIVER, null, lat, lng, now);
        arrivedAt = now;
    }

    public void start(UUID actorId, String pin, boolean pinRequired, Double lat, Double lng, Instant now) {
        // Chuyen cho khach: tai xe nhap PIN khach doc -> dung nguoi dung xe (FR-TRIP-012)
        if (serviceType == ServiceType.RIDE && pinRequired && !Secrets.constantTimeEquals(startPin, pin)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Mã PIN không đúng, vui lòng hỏi lại khách.");
        }
        transitionTo(TripStatus.IN_TRIP, actorId, TripActorRole.DRIVER, null, lat, lng, now);
        startedAt = now;
    }

    public void complete(UUID actorId, String code, Double lat, Double lng, Instant now) {
        // Giao hang: OTP la bang chung giao dung nguoi nhan (FR-TRIP-013)
        if (serviceType == ServiceType.DELIVERY && !Secrets.constantTimeEquals(deliveryCode, code)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Mã giao hàng không đúng.");
        }
        transitionTo(TripStatus.COMPLETED, actorId, TripActorRole.DRIVER, null, lat, lng, now);
        completedAt = now;
    }

    public void cancel(UUID actorId, TripActorRole role, CancelReason reason, String note, BigDecimal fee, Instant now) {
        transitionTo(TripStatus.CANCELLED, actorId, role, reason.name() + (note == null ? "" : ": " + note), null, null, now);
        cancelledByRole = role;
        cancelReasonCode = reason;
        cancelNote = note;
        cancellationFee = fee;
    }

    public void recordFinalFare(BigDecimal amount, Instant now) {
        finalFare = amount;
        updatedAt = now;
    }

    public boolean hasPendingDispatch(Instant now) {
        return status == TripStatus.MATCHING && nextDispatchAt != null && !nextDispatchAt.isAfter(now);
    }

    public record DeliveryDetails(String recipientName, String recipientPhone, String packageDescription, Double packageWeightKg) {}
}
