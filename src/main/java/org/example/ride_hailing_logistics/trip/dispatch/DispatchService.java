package org.example.ride_hailing_logistics.trip.dispatch;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.tx.AfterCommit;
import org.example.ride_hailing_logistics.driver.DriverAvailabilityService;
import org.example.ride_hailing_logistics.driver.EligibleDriverRow;
import org.example.ride_hailing_logistics.location.LocationService;
import org.example.ride_hailing_logistics.location.LocationService.NearbyDriver;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.example.ride_hailing_logistics.trip.ActiveTripRegistry;
import org.example.ride_hailing_logistics.trip.Trip;
import org.example.ride_hailing_logistics.trip.TripEvents;
import org.example.ride_hailing_logistics.trip.TripLocalEvents;
import org.example.ride_hailing_logistics.trip.TripRepository;
import org.example.ride_hailing_logistics.trip.TripStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Matching va offer (FR-MAT-001...015).
 *
 * Thu tu khoa o MOI luong (dispatch, chap nhan, tu choi, het han, huy): hang trip -> hang offer -> hang driver.
 * Cung mot thu tu nen khong co deadlock, va moi quyet dinh ve mot chuyen duoc xep hang tren khoa hang trip.
 */
@Slf4j
@Service
public class DispatchService {

    private final TripRepository trips;
    private final DriverOfferRepository offers;
    private final LocationService locationService;
    private final DriverAvailabilityService driverAvailability;
    private final ActiveTripRegistry activeTrips;
    private final OutboxService outbox;
    private final ApplicationEventPublisher events;
    private final AppProperties.Dispatch props;
    private final Clock clock;

    public DispatchService(TripRepository trips, DriverOfferRepository offers, LocationService locationService,
                           DriverAvailabilityService driverAvailability, ActiveTripRegistry activeTrips,
                           OutboxService outbox, ApplicationEventPublisher events, AppProperties appProperties, Clock clock) {
        this.trips = trips;
        this.offers = offers;
        this.locationService = locationService;
        this.driverAvailability = driverAvailability;
        this.activeTrips = activeTrips;
        this.outbox = outbox;
        this.events = events;
        this.props = appProperties.dispatch();
        this.clock = clock;
    }

    /**
     * Tim ung vien va gui offer cho nguoi tot nhat. Goi lap lai an toan: chuyen khong con MATCHING
     * hoac dang co offer PENDING thi khong lam gi.
     */
    @Transactional
    public void dispatchNext(UUID tripId) {
        Trip trip = trips.findByIdForUpdate(tripId).orElse(null);
        if (trip == null || trip.getStatus() != TripStatus.MATCHING) return;
        if (!offers.findByTripIdAndStatus(tripId, DriverOffer.Status.PENDING).isEmpty()) return;

        Instant now = clock.instant();
        if (!trip.getMatchingStartedAt().plus(props.matchingTimeout()).isAfter(now)) {
            markNoDriver(trip, now);   // FR-MAT-015
            return;
        }
        // Tai xe da tu choi / het han offer cua chuyen nay khong duoc moi lai
        Set<UUID> alreadyOffered = offers.findByTripId(tripId).stream().map(DriverOffer::getDriverId).collect(Collectors.toSet());
        List<Double> radii = props.radiiKm();
        while (true) {
            double radiusKm = radii.get(Math.min(trip.getMatchingRound(), radii.size() - 1));
            Optional<DriverOffer> offer = tryOffer(trip, radiusKm * 1000, alreadyOffered, now);
            if (offer.isPresent()) return;
            if (trip.getMatchingRound() < radii.size() - 1) {
                trip.advanceMatchingRound();   // mo rong ban kinh theo vong (FR-MAT-004)
                continue;
            }
            trip.scheduleNextDispatch(now.plus(props.retryInterval()));
            return;
        }
    }

    private Optional<DriverOffer> tryOffer(Trip trip, double radiusMeters, Set<UUID> excluded, Instant now) {
        List<NearbyDriver> nearby = locationService.findNearby(trip.getPickupLat(), trip.getPickupLng(), radiusMeters,
                trip.getVehicleType(), props.maxCandidates());
        if (nearby.isEmpty()) return Optional.empty();
        Map<UUID, NearbyDriver> byId = nearby.stream().collect(Collectors.toMap(NearbyDriver::driverId, Function.identity(), (a, b) -> a));
        Set<UUID> eligible = driverAvailability.filterEligible(byId.keySet(), trip.getVehicleType()).stream()
                .map(EligibleDriverRow::driverId).collect(Collectors.toSet());
        List<CandidateRanker.Candidate> ranked = CandidateRanker.rank(eligible.stream()
                .map(id -> new CandidateRanker.Candidate(id, byId.get(id).distanceMeters(), byId.get(id).locationAgeMillis()))
                .toList(), excluded);
        for (CandidateRanker.Candidate candidate : ranked) {
            // Giu tai xe nguyen tu: luong khac vua giu thi reserve tra false, chuyen sang nguoi ke tiep (FR-DRV-012)
            if (!driverAvailability.reserve(candidate.driverId())) continue;
            Instant expiresAt = now.plus(props.offerTimeout());
            DriverOffer offer = offers.saveAndFlush(DriverOffer.create(trip.getId(), candidate.driverId(),
                    trip.getMatchingRound(), (int) Math.round(candidate.distanceMeters()), now, expiresAt));
            // Luoi an toan neu job het han bi tre: lan dispatch tiep theo khong som hon luc offer het han
            trip.scheduleNextDispatch(expiresAt.plusSeconds(1));
            events.publishEvent(new TripLocalEvents.OfferCreated(offer.getId(), trip.getId(), candidate.driverId(),
                    trip.getServiceType().name(), trip.getPickupLat(), trip.getPickupLng(), trip.getPickupAddress(),
                    trip.getDropoffLat(), trip.getDropoffLng(), trip.getDropoffAddress(), offer.getDistanceMeters(),
                    trip.getQuotedTotal(), trip.getCurrency(), expiresAt));
            log.info("Offer {} for trip {} sent to driver {} ({} m, round {})", offer.getId(), trip.getId(),
                    candidate.driverId(), offer.getDistanceMeters(), trip.getMatchingRound());
            return Optional.of(offer);
        }
        return Optional.empty();
    }

    /**
     * Chap nhan offer — thao tac nguyen tu kiem dong thoi trip, offer va driver (FR-MAT-010, CON-06).
     * 1. Khoa hang trip: moi request chap nhan cho chuyen nay xep hang tai day.
     * 2. Khoa hang offer va kiem PENDING + chua het han.
     * 3. Tai xe OFFERED -> BUSY bang cap nhat co dieu kien.
     * 4. Partial unique index trong DB chan not neu co lo hong nao o tren.
     */
    @Transactional
    public Trip accept(UUID offerId, UUID driverId) {
        UUID tripId = offers.findTripIdForDriver(offerId, driverId).orElseThrow(() -> BusinessException.notFound("đề nghị chuyến"));
        Trip trip = trips.findByIdForUpdate(tripId).orElseThrow(() -> BusinessException.notFound("chuyến"));
        DriverOffer offer = offers.findByIdForUpdate(offerId).orElseThrow();
        Instant now = clock.instant();

        if (offer.getStatus() == DriverOffer.Status.ACCEPTED) {
            return trip;   // gui lap request da thang -> cung ket qua logic (FR-MAT-012)
        }
        if (!offer.isPending() || offer.isExpired(now)) {
            throw BusinessException.conflict(ErrorCode.OFFER_EXPIRED, "Đề nghị chuyến đã hết hạn hoặc không còn hiệu lực.");
        }
        if (trip.getStatus() != TripStatus.MATCHING) {
            throw BusinessException.conflict(ErrorCode.DRIVER_ALREADY_ASSIGNED, "Chuyến không còn chờ tài xế.");
        }
        if (!driverAvailability.markBusy(driverId)) {
            throw BusinessException.conflict(ErrorCode.DRIVER_ALREADY_ASSIGNED, "Tài xế không còn ở trạng thái nhận chuyến.");
        }
        offer.close(DriverOffer.Status.ACCEPTED, now);
        TripStatus old = trip.getStatus();
        trip.assignDriver(driverAvailability.snapshot(driverId), now);
        // trip dang duoc quan ly: chi flush (save() se merge va lam mat lich su trang thai moi them)
        // flush ngay de vi pham unique index (neu co) bung ra trong transaction nay
        trips.flush();

        outbox.append("trip", "Trip", trip.getId(), TripEvents.TRIP_ACCEPTED, 1,
                new TripEvents.TripAccepted(trip.getId(), driverId, now));
        publishStatusChanged(trip, old, now);
        AfterCommit.run(() -> activeTrips.put(driverId, trip.getId()));
        return trip;
    }

    @Transactional
    public void decline(UUID offerId, UUID driverId) {
        UUID tripId = offers.findTripIdForDriver(offerId, driverId).orElseThrow(() -> BusinessException.notFound("đề nghị chuyến"));
        trips.findByIdForUpdate(tripId);
        DriverOffer offer = offers.findByIdForUpdate(offerId).orElseThrow();
        if (!offer.isPending()) return;   // tu choi lap / offer da het han: khong lam gi them
        closeOffer(offer, DriverOffer.Status.DECLINED, "DECLINED", clock.instant());
        events.publishEvent(new TripLocalEvents.DispatchRequested(tripId));   // FR-MAT-013
    }

    /** Offer qua han: tra tai xe ve AVAILABLE dung mot lan, chuyen sang ung vien ke (FR-DRV-013, FR-MAT-013). */
    @Transactional
    public void expire(UUID offerId) {
        UUID tripId = offers.findTripId(offerId).orElse(null);
        if (tripId == null) return;
        trips.findByIdForUpdate(tripId);
        DriverOffer offer = offers.findByIdForUpdate(offerId).orElseThrow();
        Instant now = clock.instant();
        if (!offer.isPending() || !offer.isExpired(now)) return;
        closeOffer(offer, DriverOffer.Status.EXPIRED, "EXPIRED", now);
        events.publishEvent(new TripLocalEvents.DispatchRequested(tripId));
    }

    /** Goi tu TripService khi huy chuyen, trong cung transaction da khoa hang trip (FR-CAN-005). */
    @Transactional
    public void cancelOpenOffers(UUID tripId) {
        Instant now = clock.instant();
        for (DriverOffer offer : offers.findByTripIdAndStatus(tripId, DriverOffer.Status.PENDING)) {
            closeOffer(offer, DriverOffer.Status.CANCELLED, "TRIP_CANCELLED", now);
        }
    }

    @Transactional(readOnly = true)
    public Optional<OfferView> currentOfferFor(UUID driverId) {
        return offers.findFirstByDriverIdAndStatus(driverId, DriverOffer.Status.PENDING)
                .filter(o -> !o.isExpired(clock.instant()))
                .flatMap(o -> trips.findById(o.getTripId()).map(t -> OfferView.of(o, t)));
    }

    private void closeOffer(DriverOffer offer, DriverOffer.Status status, String reason, Instant now) {
        offer.close(status, now);
        if (!driverAvailability.releaseOffer(offer.getDriverId())) {
            log.warn("Driver {} was not OFFERED when closing offer {}", offer.getDriverId(), offer.getId());
        }
        events.publishEvent(new TripLocalEvents.OfferClosed(offer.getId(), offer.getTripId(), offer.getDriverId(), reason));
    }

    private void markNoDriver(Trip trip, Instant now) {
        TripStatus old = trip.getStatus();
        trip.markNoDriver(now);
        publishStatusChanged(trip, old, now);
        log.info("Trip {} ended with NO_DRIVER after {} rounds", trip.getId(), trip.getMatchingRound() + 1);
    }

    private void publishStatusChanged(Trip trip, TripStatus old, Instant now) {
        events.publishEvent(new TripLocalEvents.TripStatusChanged(trip.getId(), trip.getCode(), trip.getCustomerId(),
                trip.getDriverId(), old, trip.getStatus(), trip.getVersion(), now));
    }

    /** Tai xe chi thay thong tin can de quyet dinh nhan chuyen — khong ten/SDT khach (FR-MAT-008, FR-LOC-012). */
    public record OfferView(UUID offerId, UUID tripId, String serviceType, double pickupLat, double pickupLng,
                            String pickupAddress, String dropoffAddress, int estimatedPickupDistanceMeters,
                            java.math.BigDecimal estimatedFare, String currency, Instant expiresAt) {
        static OfferView of(DriverOffer o, Trip t) {
            return new OfferView(o.getId(), t.getId(), t.getServiceType().name(), t.getPickupLat(), t.getPickupLng(),
                    t.getPickupAddress(), t.getDropoffAddress(), o.getDistanceMeters(), t.getQuotedTotal(), t.getCurrency(),
                    o.getExpiresAt());
        }
    }
}
