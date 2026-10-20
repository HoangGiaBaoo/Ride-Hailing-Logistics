package org.example.ride_hailing_logistics.trip;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.tx.AfterCommit;
import org.example.ride_hailing_logistics.common.util.GeoUtils;
import org.example.ride_hailing_logistics.common.util.Normalizers;
import org.example.ride_hailing_logistics.driver.DriverAvailabilityService;
import org.example.ride_hailing_logistics.location.LocationService;
import org.example.ride_hailing_logistics.platform.audit.AuditService;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.QuoteSnapshot;
import org.example.ride_hailing_logistics.pricing.QuoteService;
import org.example.ride_hailing_logistics.trip.TripDtos.CancellationPreview;
import org.example.ride_hailing_logistics.trip.TripDtos.CreateTripRequest;
import org.example.ride_hailing_logistics.trip.TripDtos.HistoryEntry;
import org.example.ride_hailing_logistics.trip.TripDtos.TransitionRequest;
import org.example.ride_hailing_logistics.trip.TripDtos.TripResponse;
import org.example.ride_hailing_logistics.trip.dispatch.DispatchService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class TripService {

    private final TripRepository trips;
    private final TripStatusHistoryRepository historyRepository;
    private final QuoteService quoteService;
    private final DispatchService dispatchService;
    private final DriverAvailabilityService driverAvailability;
    private final LocationService locationService;
    private final ActiveTripRegistry activeTrips;
    private final OutboxService outbox;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final CancellationPolicy cancellationPolicy;
    private final AppProperties.Trip props;
    private final Clock clock;

    public TripService(TripRepository trips, TripStatusHistoryRepository historyRepository, QuoteService quoteService,
                       DispatchService dispatchService, DriverAvailabilityService driverAvailability,
                       LocationService locationService, ActiveTripRegistry activeTrips, OutboxService outbox,
                       AuditService audit, ApplicationEventPublisher events, AppProperties appProperties, Clock clock) {
        this.trips = trips;
        this.historyRepository = historyRepository;
        this.quoteService = quoteService;
        this.dispatchService = dispatchService;
        this.driverAvailability = driverAvailability;
        this.locationService = locationService;
        this.activeTrips = activeTrips;
        this.outbox = outbox;
        this.audit = audit;
        this.events = events;
        this.props = appProperties.trip();
        this.cancellationPolicy = new CancellationPolicy(props.freeCancelWindow(), props.noShowWait(),
                props.cancelFeeAfterAccept(), props.cancelFeeArrived());
        this.clock = clock;
    }

    // ------------------------------------------------------------------ tao chuyen (UC-03)

    @Transactional
    public TripResponse create(UUID customerId, CreateTripRequest req) {
        if (trips.existsByCustomerIdAndStatusIn(customerId, TripStatus.CUSTOMER_ACTIVE)) {
            // FR-TRIP-007; partial unique index uq_trip_active_customer chan not truong hop dong thoi
            throw BusinessException.rule("Bạn đang có một chuyến chưa kết thúc.");
        }
        QuoteSnapshot quote = quoteService.consumeQuote(req.quoteId(), customerId);
        if (quote.surgeMultiplier().compareTo(BigDecimal.ONE) > 0 && !req.surgeConfirmed()) {
            // BR-006, FR-PRI-010: khach phai xac nhan he so surge truoc khi dat
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Giá đang tăng x" + quote.surgeMultiplier().stripTrailingZeros().toPlainString()
                            + ", vui lòng xác nhận trước khi đặt chuyến.", Map.of("surgeMultiplier", quote.surgeMultiplier()));
        }
        Trip.DeliveryDetails delivery = quote.serviceType() == ServiceType.DELIVERY ? deliveryDetails(req, quote.vehicleType()) : null;
        Instant now = clock.instant();
        Trip trip = Trip.create(quote, req.paymentMethod(), delivery, now);
        trip.startMatching(now);   // FR-TRIP-006: CREATED -> MATCHING
        trips.saveAndFlush(trip);

        outbox.append("trip", "Trip", trip.getId(), TripEvents.TRIP_REQUESTED, 1, new TripEvents.TripRequested(
                trip.getId(), trip.getServiceType().name(), trip.getVehicleType().name(), trip.getPickupLat(),
                trip.getPickupLng(), trip.getQuoteId(), now));
        publishStatus(trip, TripStatus.CREATED, now);
        events.publishEvent(new TripLocalEvents.DispatchRequested(trip.getId()));
        return TripResponse.of(trip, TripActorRole.CUSTOMER);
    }

    private Trip.DeliveryDetails deliveryDetails(CreateTripRequest req, VehicleType vehicleType) {
        if (req.recipient() == null || req.packageDescription() == null || req.packageDescription().isBlank()
                || req.packageWeightKg() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giao hàng cần thông tin người nhận, mô tả và khối lượng kiện hàng.");
        }
        double limit = vehicleType == VehicleType.VAN ? props.maxPackageWeightVanKg() : props.maxPackageWeightMotorbikeKg();
        if (req.packageWeightKg() > limit) {
            throw BusinessException.rule("Kiện hàng vượt quá " + (int) limit + " kg cho loại xe đã chọn.");
        }
        String phone = Normalizers.phone(req.recipient().phone())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Số điện thoại người nhận không hợp lệ."));
        return new Trip.DeliveryDetails(req.recipient().name().trim(), phone, req.packageDescription().trim(), req.packageWeightKg());
    }

    // ------------------------------------------------------------------ xem chuyen

    @Transactional(readOnly = true)
    public TripResponse get(UUID tripId, Actor actor) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> BusinessException.notFound("chuyến"));
        return TripResponse.of(trip, viewerRole(trip, actor));
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID tripId, Actor actor) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> BusinessException.notFound("chuyến"));
        viewerRole(trip, actor);
        return historyRepository.findByTripIdOrderByIdAsc(tripId).stream().map(HistoryEntry::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<TripResponse> mine(Actor actor, boolean asDriver, Pageable pageable) {
        if (asDriver) {
            return PageResponse.of(trips.findByDriverId(actor.id(), pageable), t -> TripResponse.of(t, TripActorRole.DRIVER));
        }
        return PageResponse.of(trips.findByCustomerId(actor.id(), pageable), t -> TripResponse.of(t, TripActorRole.CUSTOMER));
    }

    /** FR-ADM-001: tim theo ma, khach, tai xe, dich vu, trang thai, khoang thoi gian; luon phan trang. */
    @Transactional(readOnly = true)
    public PageResponse<TripResponse> search(String code, UUID customerId, UUID driverId, ServiceType serviceType,
                                             TripStatus status, Instant from, Instant to, Pageable pageable) {
        Specification<Trip> spec = (root, query, cb) -> cb.conjunction();
        if (code != null && !code.isBlank()) spec = spec.and((r, q, cb) -> cb.equal(r.get("code"), code.trim().toUpperCase()));
        if (customerId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("customerId"), customerId));
        if (driverId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("driverId"), driverId));
        if (serviceType != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("serviceType"), serviceType));
        if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (from != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), from));
        if (to != null) spec = spec.and((r, q, cb) -> cb.lessThan(r.get("createdAt"), to));
        return PageResponse.of(trips.findAll(spec, pageable), t -> TripResponse.of(t, TripActorRole.SUPPORT_STAFF));
    }

    // ------------------------------------------------------------------ tai xe chuyen trang thai (UC-04)

    @Transactional
    public TripResponse startPickup(UUID tripId, Actor driver, TransitionRequest req) {
        return driverStep(tripId, driver, TripStatus.PICKING_UP, (trip, now) ->
                trip.startPickup(driver.id(), lat(req), lng(req), now));
    }

    @Transactional
    public TripResponse arrive(UUID tripId, Actor driver, TransitionRequest req) {
        return driverStep(tripId, driver, TripStatus.ARRIVED, (trip, now) -> {
            double[] position = positionOf(driver.id(), req);
            if (position != null) {
                double meters = GeoUtils.haversineMeters(position[0], position[1], trip.getPickupLat(), trip.getPickupLng());
                if (meters > props.arrivalRadiusMeters()) {
                    // SRS §7.1: PICKING_UP -> ARRIVED khi tai xe o gan diem don
                    throw BusinessException.rule("Bạn còn cách điểm đón " + Math.round(meters) + " m.");
                }
            }
            trip.arrive(driver.id(), position == null ? null : position[0], position == null ? null : position[1], now);
        });
    }

    @Transactional
    public TripResponse start(UUID tripId, Actor driver, TransitionRequest req) {
        return driverStep(tripId, driver, TripStatus.IN_TRIP, (trip, now) ->
                trip.start(driver.id(), req == null ? null : req.pin(), props.requireStartPin(), lat(req), lng(req), now));
    }

    @Transactional
    public TripResponse complete(UUID tripId, Actor driver, TransitionRequest req) {
        return driverStep(tripId, driver, TripStatus.COMPLETED, (trip, now) -> {
            trip.complete(driver.id(), req == null ? null : req.deliveryCode(), lat(req), lng(req), now);
            driverAvailability.finishTrip(driver.id());
            // FR-TRIP-014: chot du lieu, phat TripCompleted dung MOT lan — buoc chuyen co khoa chi di qua day mot lan
            outbox.append("trip", "Trip", trip.getId(), TripEvents.TRIP_COMPLETED, 1, new TripEvents.TripCompleted(
                    trip.getId(), trip.getCode(), trip.getCustomerId(), trip.getDriverId(), trip.getQuoteId(),
                    trip.getServiceType().name(), trip.getPaymentMethod().name(), trip.getDistanceMeters(),
                    trip.getDurationSeconds(), now));
            AfterCommit.run(() -> activeTrips.remove(driver.id()));
        });
    }

    private TripResponse driverStep(UUID tripId, Actor driver, TripStatus target, Step step) {
        Trip trip = trips.findByIdForUpdate(tripId).orElseThrow(() -> BusinessException.notFound("chuyến"));
        if (!driver.id().equals(trip.getDriverId())) {
            throw BusinessException.notFound("chuyến");   // tai xe khac khong duoc biet chuyen nay ton tai
        }
        if (trip.getStatus() == target) {
            return TripResponse.of(trip, TripActorRole.DRIVER);   // gui lap (mang chap chon): tra trang thai hien tai
        }
        TripStatus old = trip.getStatus();
        Instant now = clock.instant();
        step.apply(trip, now);
        trips.flush();
        publishStatus(trip, old, now);
        return TripResponse.of(trip, TripActorRole.DRIVER);
    }

    // ------------------------------------------------------------------ huy chuyen (UC-08)

    @Transactional(readOnly = true)
    public CancellationPreview previewCancellation(UUID tripId, Actor actor) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> BusinessException.notFound("chuyến"));
        TripActorRole role = viewerRole(trip, actor);
        if (!TripStateMachine.isAllowed(trip.getStatus(), TripStatus.CANCELLED, role)) {
            return new CancellationPreview(false, BigDecimal.ZERO, trip.getCurrency(), CancellationPolicy.VERSION,
                    "Không thể hủy chuyến ở trạng thái hiện tại.");
        }
        BigDecimal fee = cancellationPolicy.feeFor(role, trip.getStatus(), trip.getAcceptedAt(), trip.getArrivedAt(), clock.instant());
        return new CancellationPreview(true, fee, trip.getCurrency(), CancellationPolicy.VERSION,
                fee.signum() > 0 ? "Hủy lúc này sẽ phát sinh phí cho khách hàng." : "Hủy miễn phí.");
    }

    @Transactional
    public TripResponse cancel(UUID tripId, Actor actor, CancelReason reason, String note, boolean staffException) {
        if (reason == CancelReason.OTHER && (note == null || note.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng mô tả lý do hủy.");
        }
        if (staffException && (note == null || note.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Hủy ngoại lệ bắt buộc ghi rõ lý do.");   // FR-CAN-007
        }
        Trip trip = trips.findByIdForUpdate(tripId).orElseThrow(() -> BusinessException.notFound("chuyến"));
        TripActorRole role = staffException ? TripActorRole.SUPPORT_STAFF : viewerRole(trip, actor);
        if (role == TripActorRole.SUPPORT_STAFF && !staffException) {
            throw BusinessException.notFound("chuyến");   // nhan vien huy phai di qua endpoint ngoai le co audit
        }
        if (trip.getStatus() == TripStatus.CANCELLED) {
            return TripResponse.of(trip, role);   // huy lap: khong thu/hoan phi lan nua (FR-CAN-006)
        }
        Instant now = clock.instant();
        BigDecimal fee = cancellationPolicy.feeFor(role, trip.getStatus(), trip.getAcceptedAt(), trip.getArrivedAt(), now);
        TripStatus old = trip.getStatus();
        UUID assignedDriver = trip.getDriverId();
        trip.cancel(actor.id(), role, reason, note == null ? null : note.trim(), fee, now);
        dispatchService.cancelOpenOffers(tripId);
        if (assignedDriver != null && TripStatus.DRIVER_ACTIVE.contains(old)) {
            driverAvailability.finishTrip(assignedDriver);   // giai phong tai xe (FR-CAN-005)
            AfterCommit.run(() -> activeTrips.remove(assignedDriver));
        }
        trips.flush();
        outbox.append("trip", "Trip", trip.getId(), TripEvents.TRIP_CANCELLED, 1, new TripEvents.TripCancelled(
                trip.getId(), trip.getCode(), trip.getCustomerId(), assignedDriver, trip.getServiceType().name(),
                trip.getPaymentMethod().name(), role.name(), reason.name(), fee, trip.getCurrency(),
                CancellationPolicy.VERSION, now));
        if (staffException) {
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("from", old);
            delta.put("reason", reason);
            delta.put("note", note);
            audit.record(actor, "TRIP_CANCELLED_BY_STAFF", "Trip", tripId, delta);
        }
        publishStatus(trip, old, now);
        return TripResponse.of(trip, role);
    }

    /** FareFinalized tu pricing: luu cuoc cuoi vao chuyen de hien bien nhan (SRS §5.6). */
    @Transactional
    public void recordFinalFare(UUID tripId, BigDecimal amount) {
        trips.findByIdForUpdate(tripId).ifPresent(t -> t.recordFinalFare(amount, clock.instant()));
    }

    // ------------------------------------------------------------------ helpers

    /** Kiem quyen so huu (FR-IAM-010). Khong lien quan -> 404 de khong lo chuyen ton tai. */
    TripActorRole viewerRole(Trip trip, Actor actor) {
        TripActorRole role = TripActorRole.of(actor, trip);
        if (role == null && (actor.has(org.example.ride_hailing_logistics.common.security.Role.FINANCE_STAFF))) {
            role = TripActorRole.SUPPORT_STAFF;
        }
        if (role == null) {
            throw BusinessException.notFound("chuyến");
        }
        return role;
    }

    private double[] positionOf(UUID driverId, TransitionRequest req) {
        if (req != null && req.latitude() != null && req.longitude() != null) {
            return new double[]{req.latitude(), req.longitude()};
        }
        return locationService.latest(driverId).map(l -> new double[]{l.latitude(), l.longitude()}).orElse(null);
    }

    private void publishStatus(Trip trip, TripStatus old, Instant now) {
        events.publishEvent(new TripLocalEvents.TripStatusChanged(trip.getId(), trip.getCode(), trip.getCustomerId(),
                trip.getDriverId(), old, trip.getStatus(), trip.getVersion(), now));
    }

    private static Double lat(TransitionRequest req) {
        return req == null ? null : req.latitude();
    }

    private static Double lng(TransitionRequest req) {
        return req == null ? null : req.longitude();
    }

    @FunctionalInterface
    private interface Step {
        void apply(Trip trip, Instant now);
    }
}
