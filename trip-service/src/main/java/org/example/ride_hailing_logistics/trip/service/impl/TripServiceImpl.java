package org.example.ride_hailing_logistics.trip.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.common.tx.AfterCommit;
import org.example.ride_hailing_logistics.common.util.GeoUtils;
import org.example.ride_hailing_logistics.common.util.Normalizers;
import org.example.ride_hailing_logistics.platform.audit.AuditService;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.example.ride_hailing_logistics.trip.client.LocationClient;
import org.example.ride_hailing_logistics.trip.client.PricingClient;
import org.example.ride_hailing_logistics.trip.client.dto.ConsumeQuoteRequest;
import org.example.ride_hailing_logistics.trip.client.dto.DriverLocationResponse;
import org.example.ride_hailing_logistics.trip.client.dto.QuoteSnapshotResponse;
import org.example.ride_hailing_logistics.trip.client.dto.ReleaseQuoteRequest;
import org.example.ride_hailing_logistics.trip.domain.CancellationPolicy;
import org.example.ride_hailing_logistics.trip.domain.TripStateMachine;
import org.example.ride_hailing_logistics.trip.dto.request.CreateTripRequest;
import org.example.ride_hailing_logistics.trip.dto.request.TransitionRequest;
import org.example.ride_hailing_logistics.trip.dto.response.CancellationPreviewResponse;
import org.example.ride_hailing_logistics.trip.dto.response.HistoryEntryResponse;
import org.example.ride_hailing_logistics.trip.dto.response.TripResponse;
import org.example.ride_hailing_logistics.trip.entity.Trip;
import org.example.ride_hailing_logistics.trip.enums.CancelReason;
import org.example.ride_hailing_logistics.trip.enums.TripActorRole;
import org.example.ride_hailing_logistics.trip.enums.TripStatus;
import org.example.ride_hailing_logistics.trip.event.TripCancelled;
import org.example.ride_hailing_logistics.trip.event.TripCompleted;
import org.example.ride_hailing_logistics.trip.event.TripEvents;
import org.example.ride_hailing_logistics.trip.event.TripRequested;
import org.example.ride_hailing_logistics.trip.event.local.DispatchRequested;
import org.example.ride_hailing_logistics.trip.event.local.TripStatusChanged;
import org.example.ride_hailing_logistics.trip.exception.ActiveTripExistsException;
import org.example.ride_hailing_logistics.trip.exception.TripNotFoundException;
import org.example.ride_hailing_logistics.trip.mapper.TripMapper;
import org.example.ride_hailing_logistics.trip.repository.ActiveTripRegistry;
import org.example.ride_hailing_logistics.trip.repository.TripRepository;
import org.example.ride_hailing_logistics.trip.repository.TripStatusHistoryRepository;
import org.example.ride_hailing_logistics.trip.service.DispatchService;
import org.example.ride_hailing_logistics.trip.service.DriverStateService;
import org.example.ride_hailing_logistics.trip.service.TripService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class TripServiceImpl implements TripService {

    private final TripRepository trips;
    private final TripStatusHistoryRepository historyRepository;
    private final PricingClient pricingClient;
    private final LocationClient locationClient;
    private final DispatchService dispatchService;
    private final DriverStateService driverStates;
    private final ActiveTripRegistry activeTrips;
    private final OutboxService outbox;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final CancellationPolicy cancellationPolicy;
    private final TripMapper mapper;
    private final TransactionTemplate tx;
    private final AppProperties.Trip props;
    private final Clock clock;

    public TripServiceImpl(TripRepository trips, TripStatusHistoryRepository historyRepository, PricingClient pricingClient,
                           LocationClient locationClient, DispatchService dispatchService, DriverStateService driverStates,
                           ActiveTripRegistry activeTrips, OutboxService outbox, AuditService audit,
                           ApplicationEventPublisher events, CancellationPolicy cancellationPolicy, TripMapper mapper,
                           TransactionTemplate tx, AppProperties appProperties, Clock clock) {
        this.trips = trips;
        this.historyRepository = historyRepository;
        this.pricingClient = pricingClient;
        this.locationClient = locationClient;
        this.dispatchService = dispatchService;
        this.driverStates = driverStates;
        this.activeTrips = activeTrips;
        this.outbox = outbox;
        this.audit = audit;
        this.events = events;
        this.cancellationPolicy = cancellationPolicy;
        this.mapper = mapper;
        this.tx = tx;
        this.props = appProperties.trip();
        this.clock = clock;
    }

    // ------------------------------------------------------------------ tao chuyen (UC-03)

    /**
     * tripId suy ra tu (khach, Idempotency-Key): gui lai sau khi pricing da tieu quote van ra cung tripId,
     * pricing nhan ra va tra lai cung snapshot thay vi bao "quote da dung" (BR-015).
     * Tieu quote goi ngoai transaction; transaction cuc bo that bai thi tra quote lai (bu tru).
     */
    @Override
    public TripResponse create(UUID customerId, String idempotencyKey, CreateTripRequest req) {
        UUID tripId = UUID.nameUUIDFromBytes(("trip:" + customerId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
        Trip existing = trips.findById(tripId).orElse(null);
        if (existing != null) {
            return mapper.toResponse(existing, TripActorRole.CUSTOMER);
        }
        if (trips.existsByCustomerIdAndStatusIn(customerId, TripStatus.CUSTOMER_ACTIVE)) {
            // FR-TRIP-007; partial unique index uq_trip_active_customer chan not truong hop dong thoi
            throw new ActiveTripExistsException();
        }
        QuoteSnapshotResponse quote = pricingClient.consume(req.quoteId(), new ConsumeQuoteRequest(customerId, tripId)).data();
        try {
            return tx.execute(s -> persistNewTrip(tripId, quote, req));
        } catch (RuntimeException e) {
            releaseQuote(quote.quoteId(), tripId);
            throw e;
        }
    }

    private TripResponse persistNewTrip(UUID tripId, QuoteSnapshotResponse quote, CreateTripRequest req) {
        if (quote.surgeMultiplier().compareTo(BigDecimal.ONE) > 0 && !req.surgeConfirmed()) {
            // BR-006, FR-PRI-010: khach phai xac nhan he so surge truoc khi dat
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Giá đang tăng x" + quote.surgeMultiplier().stripTrailingZeros().toPlainString()
                            + ", vui lòng xác nhận trước khi đặt chuyến.", Map.of("surgeMultiplier", quote.surgeMultiplier()));
        }
        Trip.DeliveryDetails delivery = quote.serviceType() == ServiceType.DELIVERY ? deliveryDetails(req, quote.vehicleType()) : null;
        Instant now = clock.instant();
        Trip trip = Trip.create(tripId, quote, req.paymentMethod(), delivery, now);
        trip.startMatching(now);   // FR-TRIP-006: CREATED -> MATCHING
        trips.saveAndFlush(trip);

        outbox.append(TripEvents.PRODUCER, "Trip", trip.getId(), TripEvents.TRIP_REQUESTED, 1, new TripRequested(
                trip.getId(), trip.getServiceType().name(), trip.getVehicleType().name(), trip.getPickupLat(),
                trip.getPickupLng(), trip.getQuoteId(), now));
        publishStatus(trip, TripStatus.CREATED, now);
        events.publishEvent(new DispatchRequested(trip.getId()));
        return mapper.toResponse(trip, TripActorRole.CUSTOMER);
    }

    private void releaseQuote(UUID quoteId, UUID tripId) {
        try {
            pricingClient.release(quoteId, new ReleaseQuoteRequest(tripId));
        } catch (RuntimeException e) {
            // Khong tra duoc: quote het han theo TTL, khach lay bao gia moi
            log.warn("Could not release quote {} for trip {}: {}", quoteId, tripId, e.getMessage());
        }
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

    @Override
    @Transactional(readOnly = true)
    public TripResponse get(UUID tripId, Actor actor) {
        Trip trip = trips.findById(tripId).orElseThrow(TripNotFoundException::new);
        return mapper.toResponse(trip, viewerRole(trip, actor));
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoryEntryResponse> history(UUID tripId, Actor actor) {
        Trip trip = trips.findById(tripId).orElseThrow(TripNotFoundException::new);
        viewerRole(trip, actor);
        return historyRepository.findByTripIdOrderByIdAsc(tripId).stream().map(mapper::toHistoryEntry).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TripResponse> mine(Actor actor, boolean asDriver, Pageable pageable) {
        if (asDriver) {
            return PageResponse.of(trips.findByDriverId(actor.id(), pageable), t -> mapper.toResponse(t, TripActorRole.DRIVER));
        }
        return PageResponse.of(trips.findByCustomerId(actor.id(), pageable), t -> mapper.toResponse(t, TripActorRole.CUSTOMER));
    }

    /** FR-ADM-001: tim theo ma, khach, tai xe, dich vu, trang thai, khoang thoi gian; luon phan trang. */
    @Override
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
        return PageResponse.of(trips.findAll(spec, pageable), t -> mapper.toResponse(t, TripActorRole.SUPPORT_STAFF));
    }

    // ------------------------------------------------------------------ tai xe chuyen trang thai (UC-04)

    @Override
    @Transactional
    public TripResponse startPickup(UUID tripId, Actor driver, TransitionRequest req) {
        return driverStep(tripId, driver, TripStatus.PICKING_UP, (trip, now) ->
                trip.startPickup(driver.id(), lat(req), lng(req), now));
    }

    /**
     * SRS §7.1: PICKING_UP -> ARRIVED khi tai xe o gan diem don. Uu tien vi tri server da nhan (khong tin toa do
     * client tu khai, NFR-SEC-012); location-service khong tra loi duoc thi moi dung toa do trong request.
     */
    @Override
    public TripResponse arrive(UUID tripId, Actor driver, TransitionRequest req) {
        double[] position = positionOf(driver.id(), req);
        return tx.execute(s -> driverStep(tripId, driver, TripStatus.ARRIVED, (trip, now) -> {
            if (position != null) {
                double meters = GeoUtils.haversineMeters(position[0], position[1], trip.getPickupLat(), trip.getPickupLng());
                if (meters > props.arrivalRadiusMeters()) {
                    throw BusinessException.rule("Bạn còn cách điểm đón " + Math.round(meters) + " m.");
                }
            }
            trip.arrive(driver.id(), position == null ? null : position[0], position == null ? null : position[1], now);
        }));
    }

    @Override
    @Transactional
    public TripResponse start(UUID tripId, Actor driver, TransitionRequest req) {
        return driverStep(tripId, driver, TripStatus.IN_TRIP, (trip, now) ->
                trip.start(driver.id(), req == null ? null : req.pin(), props.requireStartPin(), lat(req), lng(req), now));
    }

    @Override
    @Transactional
    public TripResponse complete(UUID tripId, Actor driver, TransitionRequest req) {
        return driverStep(tripId, driver, TripStatus.COMPLETED, (trip, now) -> {
            trip.complete(driver.id(), req == null ? null : req.deliveryCode(), lat(req), lng(req), now);
            driverStates.finishTrip(driver.id());
            // FR-TRIP-014: TripCompleted phat dung MOT lan — buoc chuyen co khoa chi di qua day mot lan
            outbox.append(TripEvents.PRODUCER, "Trip", trip.getId(), TripEvents.TRIP_COMPLETED, 1, new TripCompleted(
                    trip.getId(), trip.getCode(), trip.getCustomerId(), trip.getDriverId(), trip.getQuoteId(),
                    trip.getServiceType().name(), trip.getPaymentMethod().name(), trip.getDistanceMeters(),
                    trip.getDurationSeconds(), now));
            AfterCommit.run(() -> activeTrips.remove(driver.id()));
        });
    }

    private TripResponse driverStep(UUID tripId, Actor driver, TripStatus target, Step step) {
        Trip trip = trips.findByIdForUpdate(tripId).orElseThrow(TripNotFoundException::new);
        if (!driver.id().equals(trip.getDriverId())) {
            throw new TripNotFoundException();   // tai xe khac khong duoc biet chuyen nay ton tai
        }
        if (trip.getStatus() == target) {
            return mapper.toResponse(trip, TripActorRole.DRIVER);   // gui lap (mang chap chon): tra trang thai hien tai
        }
        TripStatus old = trip.getStatus();
        Instant now = clock.instant();
        step.apply(trip, now);
        trips.flush();
        publishStatus(trip, old, now);
        return mapper.toResponse(trip, TripActorRole.DRIVER);
    }

    // ------------------------------------------------------------------ huy chuyen (UC-08)

    @Override
    @Transactional(readOnly = true)
    public CancellationPreviewResponse previewCancellation(UUID tripId, Actor actor) {
        Trip trip = trips.findById(tripId).orElseThrow(TripNotFoundException::new);
        TripActorRole role = viewerRole(trip, actor);
        if (!TripStateMachine.isAllowed(trip.getStatus(), TripStatus.CANCELLED, role)) {
            return new CancellationPreviewResponse(false, BigDecimal.ZERO, trip.getCurrency(), CancellationPolicy.VERSION,
                    "Không thể hủy chuyến ở trạng thái hiện tại.");
        }
        BigDecimal fee = cancellationPolicy.feeFor(role, trip.getStatus(), trip.getAcceptedAt(), trip.getArrivedAt(), clock.instant());
        return new CancellationPreviewResponse(true, fee, trip.getCurrency(), CancellationPolicy.VERSION,
                fee.signum() > 0 ? "Hủy lúc này sẽ phát sinh phí cho khách hàng." : "Hủy miễn phí.");
    }

    @Override
    @Transactional
    public TripResponse cancel(UUID tripId, Actor actor, CancelReason reason, String note, boolean staffException) {
        if (reason == CancelReason.OTHER && (note == null || note.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng mô tả lý do hủy.");
        }
        if (staffException && (note == null || note.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Hủy ngoại lệ bắt buộc ghi rõ lý do.");   // FR-CAN-007
        }
        Trip trip = trips.findByIdForUpdate(tripId).orElseThrow(TripNotFoundException::new);
        TripActorRole role = staffException ? TripActorRole.SUPPORT_STAFF : viewerRole(trip, actor);
        if (role == TripActorRole.SUPPORT_STAFF && !staffException) {
            throw new TripNotFoundException();   // nhan vien huy phai di qua endpoint ngoai le co audit
        }
        if (trip.getStatus() == TripStatus.CANCELLED) {
            return mapper.toResponse(trip, role);   // huy lap: khong thu/hoan phi lan nua (FR-CAN-006)
        }
        Instant now = clock.instant();
        BigDecimal fee = cancellationPolicy.feeFor(role, trip.getStatus(), trip.getAcceptedAt(), trip.getArrivedAt(), now);
        TripStatus old = trip.getStatus();
        UUID assignedDriver = trip.getDriverId();
        trip.cancel(actor.id(), role, reason, note == null ? null : note.trim(), fee, now);
        dispatchService.cancelOpenOffers(tripId);
        if (assignedDriver != null && TripStatus.DRIVER_ACTIVE.contains(old)) {
            driverStates.finishTrip(assignedDriver);   // giai phong tai xe (FR-CAN-005)
            AfterCommit.run(() -> activeTrips.remove(assignedDriver));
        }
        trips.flush();
        outbox.append(TripEvents.PRODUCER, "Trip", trip.getId(), TripEvents.TRIP_CANCELLED, 1, new TripCancelled(
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
        return mapper.toResponse(trip, role);
    }

    /** FareFinalized tu pricing: luu cuoc cuoi vao chuyen de hien bien nhan (SRS §5.6). */
    @Override
    @Transactional
    public void recordFinalFare(UUID tripId, BigDecimal amount) {
        trips.findByIdForUpdate(tripId).ifPresent(t -> t.recordFinalFare(amount, clock.instant()));
    }

    // ------------------------------------------------------------------ helpers

    /** Kiem quyen so huu (FR-IAM-010). Khong lien quan -> 404 de khong lo chuyen ton tai. */
    private TripActorRole viewerRole(Trip trip, Actor actor) {
        TripActorRole role = TripActorRole.of(actor, trip);
        if (role == null && actor.has(Role.FINANCE_STAFF)) {
            role = TripActorRole.SUPPORT_STAFF;
        }
        if (role == null) {
            throw new TripNotFoundException();
        }
        return role;
    }

    private double[] positionOf(UUID driverId, TransitionRequest req) {
        try {
            DriverLocationResponse latest = locationClient.latest(driverId).data();
            if (latest != null) return new double[]{latest.latitude(), latest.longitude()};
        } catch (BusinessException e) {
            log.debug("Server-side location unavailable for driver: {}", e.getCode());
        }
        if (req != null && req.latitude() != null && req.longitude() != null) {
            return new double[]{req.latitude(), req.longitude()};
        }
        return null;
    }

    private void publishStatus(Trip trip, TripStatus old, Instant now) {
        events.publishEvent(new TripStatusChanged(trip.getId(), trip.getCode(), trip.getCustomerId(),
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
