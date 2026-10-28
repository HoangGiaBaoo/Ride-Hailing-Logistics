package org.example.ride_hailing_logistics.trip.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.tx.AfterCommit;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.example.ride_hailing_logistics.trip.client.LocationClient;
import org.example.ride_hailing_logistics.trip.client.dto.NearbyDriverResponse;
import org.example.ride_hailing_logistics.trip.domain.CandidateRanker;
import org.example.ride_hailing_logistics.trip.dto.DispatchPlan;
import org.example.ride_hailing_logistics.trip.dto.response.OfferResponse;
import org.example.ride_hailing_logistics.trip.entity.DriverOffer;
import org.example.ride_hailing_logistics.trip.entity.Trip;
import org.example.ride_hailing_logistics.trip.enums.TripStatus;
import org.example.ride_hailing_logistics.trip.event.TripAccepted;
import org.example.ride_hailing_logistics.trip.event.TripEvents;
import org.example.ride_hailing_logistics.trip.event.local.DispatchRequested;
import org.example.ride_hailing_logistics.trip.event.local.OfferClosed;
import org.example.ride_hailing_logistics.trip.event.local.OfferCreated;
import org.example.ride_hailing_logistics.trip.event.local.TripStatusChanged;
import org.example.ride_hailing_logistics.trip.exception.OfferNotFoundException;
import org.example.ride_hailing_logistics.trip.exception.OfferUnavailableException;
import org.example.ride_hailing_logistics.trip.exception.TripNotFoundException;
import org.example.ride_hailing_logistics.trip.mapper.TripMapper;
import org.example.ride_hailing_logistics.trip.repository.ActiveTripRegistry;
import org.example.ride_hailing_logistics.trip.repository.DriverOfferRepository;
import org.example.ride_hailing_logistics.trip.repository.TripRepository;
import org.example.ride_hailing_logistics.trip.service.DispatchService;
import org.example.ride_hailing_logistics.trip.service.DriverStateService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Thu tu khoa o MOI luong (dispatch, chap nhan, tu choi, het han, huy): hang trip -> hang offer -> hang driver_states.
 * Cung mot thu tu nen khong deadlock, va moi quyet dinh ve mot chuyen xep hang tren khoa hang trip.
 *
 * Dispatch tach hai pha: (1) chot ke hoach duoi khoa trip, (2) hoi location-service NGOAI transaction,
 * (3) khoa trip lai, kiem lai roi moi giu tai xe + tao offer. Khoa Redisson chi de tranh hai instance
 * lam trung viec; dung dan van do khoa hang DB + cap nhat co dieu kien bao dam.
 */
@Slf4j
@Service
public class DispatchServiceImpl implements DispatchService {

    private static final String LOCK_PREFIX = "dispatch:lock:";

    private final TripRepository trips;
    private final DriverOfferRepository offers;
    private final LocationClient locationClient;
    private final DriverStateService driverStates;
    private final ActiveTripRegistry activeTrips;
    private final OutboxService outbox;
    private final ApplicationEventPublisher events;
    private final TripMapper mapper;
    private final RedissonClient redisson;
    private final TransactionTemplate tx;
    private final AppProperties.Dispatch props;
    private final Clock clock;

    public DispatchServiceImpl(TripRepository trips, DriverOfferRepository offers, LocationClient locationClient,
                               DriverStateService driverStates, ActiveTripRegistry activeTrips, OutboxService outbox,
                               ApplicationEventPublisher events, TripMapper mapper, RedissonClient redisson,
                               TransactionTemplate tx, AppProperties appProperties, Clock clock) {
        this.trips = trips;
        this.offers = offers;
        this.locationClient = locationClient;
        this.driverStates = driverStates;
        this.activeTrips = activeTrips;
        this.outbox = outbox;
        this.events = events;
        this.mapper = mapper;
        this.redisson = redisson;
        this.tx = tx;
        this.props = appProperties.dispatch();
        this.clock = clock;
    }

    private enum Outcome { OFFERED, NEXT_ROUND, DONE }

    @Override
    public void dispatchNext(UUID tripId) {
        RLock lock = redisson.getLock(LOCK_PREFIX + tripId);
        boolean locked;
        try {
            // Khong cho: instance/luong khac dang dispatch chuyen nay thi de no lam
            locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!locked) return;
        try {
            Outcome outcome;
            do {
                DispatchPlan plan = tx.execute(s -> plan(tripId));
                if (plan == null) return;
                List<NearbyDriverResponse> nearby;
                try {
                    nearby = locationClient.nearby(plan.pickupLat(), plan.pickupLng(), plan.radiusMeters(),
                            plan.vehicleType(), props.maxCandidates()).data();
                } catch (BusinessException e) {
                    // Khong hoi duoc vi tri: KHONG mo rong ban kinh (chua biet co tai xe hay khong), hen vong sau
                    log.warn("Nearby lookup for trip {} failed: {}", tripId, e.getMessage());
                    tx.executeWithoutResult(s -> trips.findByIdForUpdate(tripId)
                            .ifPresent(t -> t.scheduleNextDispatch(clock.instant().plus(props.retryInterval()))));
                    return;
                }
                outcome = tx.execute(s -> offerOrAdvance(plan, nearby));
            } while (outcome == Outcome.NEXT_ROUND);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    /** Pha 1: duoi khoa trip, quyet dinh co can tim khong; het thoi gian tim thi NO_DRIVER (FR-MAT-015). */
    private DispatchPlan plan(UUID tripId) {
        Trip trip = trips.findByIdForUpdate(tripId).orElse(null);
        if (trip == null || trip.getStatus() != TripStatus.MATCHING) return null;
        if (!offers.findByTripIdAndStatus(tripId, DriverOffer.Status.PENDING).isEmpty()) return null;
        Instant now = clock.instant();
        if (!trip.getMatchingStartedAt().plus(props.matchingTimeout()).isAfter(now)) {
            TripStatus old = trip.getStatus();
            trip.markNoDriver(now);
            trips.flush();
            publishStatusChanged(trip, old, now);
            log.info("Trip {} ended with NO_DRIVER after {} rounds", tripId, trip.getMatchingRound() + 1);
            return null;
        }
        List<Double> radii = props.radiiKm();
        double radiusKm = radii.get(Math.min(trip.getMatchingRound(), radii.size() - 1));
        return new DispatchPlan(tripId, trip.getPickupLat(), trip.getPickupLng(), trip.getVehicleType(),
                trip.getMatchingRound(), radiusKm * 1000);
    }

    /** Pha 3: khoa trip lai, kiem lai moi dieu kien (co the da bi huy/da co offer trong luc goi location). */
    private Outcome offerOrAdvance(DispatchPlan plan, List<NearbyDriverResponse> nearby) {
        Trip trip = trips.findByIdForUpdate(plan.tripId()).orElse(null);
        if (trip == null || trip.getStatus() != TripStatus.MATCHING || trip.getMatchingRound() != plan.round()) {
            return Outcome.DONE;
        }
        if (!offers.findByTripIdAndStatus(trip.getId(), DriverOffer.Status.PENDING).isEmpty()) return Outcome.DONE;
        Instant now = clock.instant();
        if (tryOffer(trip, nearby, now)) return Outcome.OFFERED;
        if (trip.getMatchingRound() < props.radiiKm().size() - 1) {
            trip.advanceMatchingRound();   // mo rong ban kinh theo vong (FR-MAT-004)
            return Outcome.NEXT_ROUND;
        }
        trip.scheduleNextDispatch(now.plus(props.retryInterval()));
        return Outcome.DONE;
    }

    private boolean tryOffer(Trip trip, List<NearbyDriverResponse> nearby, Instant now) {
        if (nearby == null || nearby.isEmpty()) return false;
        Map<UUID, NearbyDriverResponse> byId = nearby.stream()
                .collect(Collectors.toMap(NearbyDriverResponse::driverId, Function.identity(), (a, b) -> a));
        Set<UUID> eligible = driverStates.filterEligible(byId.keySet(), trip.getVehicleType());
        // Tai xe da tu choi / het han offer cua chuyen nay khong duoc moi lai
        Set<UUID> alreadyOffered = offers.findByTripId(trip.getId()).stream().map(DriverOffer::getDriverId)
                .collect(Collectors.toSet());
        List<CandidateRanker.Candidate> ranked = CandidateRanker.rank(eligible.stream()
                .map(id -> new CandidateRanker.Candidate(id, byId.get(id).distanceMeters(), byId.get(id).locationAgeMillis()))
                .toList(), alreadyOffered);
        for (CandidateRanker.Candidate candidate : ranked) {
            // Giu tai xe nguyen tu: luong khac vua giu thi reserve tra false, sang nguoi ke tiep (FR-DRV-012)
            if (!driverStates.reserve(candidate.driverId())) continue;
            Instant expiresAt = now.plus(props.offerTimeout());
            DriverOffer offer = offers.saveAndFlush(DriverOffer.create(trip.getId(), candidate.driverId(),
                    trip.getMatchingRound(), (int) Math.round(candidate.distanceMeters()), now, expiresAt));
            // Luoi an toan neu job het han bi tre: lan dispatch tiep theo khong som hon luc offer het han
            trip.scheduleNextDispatch(expiresAt.plusSeconds(1));
            events.publishEvent(new OfferCreated(offer.getId(), trip.getId(), candidate.driverId(),
                    trip.getServiceType().name(), trip.getPickupLat(), trip.getPickupLng(), trip.getPickupAddress(),
                    trip.getDropoffLat(), trip.getDropoffLng(), trip.getDropoffAddress(), offer.getDistanceMeters(),
                    trip.getQuotedTotal(), trip.getCurrency(), expiresAt));
            log.info("Offer {} for trip {} sent to driver {} ({} m, round {})", offer.getId(), trip.getId(),
                    candidate.driverId(), offer.getDistanceMeters(), trip.getMatchingRound());
            return true;
        }
        return false;
    }

    /**
     * Kiem DONG THOI trong mot transaction (FR-MAT-010, CON-06):
     * 1. khoa hang trip — moi request chap nhan cho chuyen nay xep hang tai day
     * 2. khoa hang offer, kiem PENDING + chua het han
     * 3. tai xe OFFERED -> BUSY bang cap nhat co dieu kien
     * 4. partial unique index trong DB chan not neu co lo hong nao o tren
     */
    @Override
    @Transactional
    public Trip accept(UUID offerId, UUID driverId) {
        UUID tripId = offers.findTripIdForDriver(offerId, driverId).orElseThrow(OfferNotFoundException::new);
        Trip trip = trips.findByIdForUpdate(tripId).orElseThrow(TripNotFoundException::new);
        DriverOffer offer = offers.findByIdForUpdate(offerId).orElseThrow(OfferNotFoundException::new);
        Instant now = clock.instant();

        if (offer.getStatus() == DriverOffer.Status.ACCEPTED) {
            return trip;   // gui lap request da thang -> cung ket qua (FR-MAT-012)
        }
        if (!offer.isPending() || offer.isExpired(now)) {
            throw OfferUnavailableException.expired();
        }
        if (trip.getStatus() != TripStatus.MATCHING) {
            throw OfferUnavailableException.alreadyAssigned("Chuyến không còn chờ tài xế.");
        }
        if (!driverStates.markBusy(driverId)) {
            throw OfferUnavailableException.alreadyAssigned("Tài xế không còn ở trạng thái nhận chuyến.");
        }
        offer.close(DriverOffer.Status.ACCEPTED, now);
        TripStatus old = trip.getStatus();
        trip.assignDriver(driverStates.require(driverId), now);
        // trip dang duoc quan ly: chi flush (save() se merge va lam mat lich su moi them);
        // flush ngay de vi pham unique index (neu co) bung ra trong transaction nay
        trips.flush();

        outbox.append(TripEvents.PRODUCER, "Trip", trip.getId(), TripEvents.TRIP_ACCEPTED, 1,
                new TripAccepted(trip.getId(), driverId, now));
        publishStatusChanged(trip, old, now);
        AfterCommit.run(() -> activeTrips.put(driverId, trip.getId()));
        return trip;
    }

    @Override
    @Transactional
    public void decline(UUID offerId, UUID driverId) {
        UUID tripId = offers.findTripIdForDriver(offerId, driverId).orElseThrow(OfferNotFoundException::new);
        trips.findByIdForUpdate(tripId);
        DriverOffer offer = offers.findByIdForUpdate(offerId).orElseThrow(OfferNotFoundException::new);
        if (!offer.isPending()) return;   // tu choi lap / offer da het han: khong lam gi them
        closeOffer(offer, DriverOffer.Status.DECLINED, "DECLINED", clock.instant());
        events.publishEvent(new DispatchRequested(tripId));   // FR-MAT-013
    }

    /** Offer qua han: tra tai xe ve AVAILABLE dung mot lan, chuyen sang ung vien ke (FR-DRV-013, FR-MAT-013). */
    @Override
    @Transactional
    public void expire(UUID offerId) {
        UUID tripId = offers.findTripId(offerId).orElse(null);
        if (tripId == null) return;
        trips.findByIdForUpdate(tripId);
        DriverOffer offer = offers.findByIdForUpdate(offerId).orElseThrow();
        Instant now = clock.instant();
        if (!offer.isPending() || !offer.isExpired(now)) return;
        closeOffer(offer, DriverOffer.Status.EXPIRED, "EXPIRED", now);
        events.publishEvent(new DispatchRequested(tripId));
    }

    @Override
    @Transactional
    public void cancelOpenOffers(UUID tripId) {
        Instant now = clock.instant();
        for (DriverOffer offer : offers.findByTripIdAndStatus(tripId, DriverOffer.Status.PENDING)) {
            closeOffer(offer, DriverOffer.Status.CANCELLED, "TRIP_CANCELLED", now);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OfferResponse> currentOfferFor(UUID driverId) {
        return offers.findFirstByDriverIdAndStatus(driverId, DriverOffer.Status.PENDING)
                .filter(o -> !o.isExpired(clock.instant()))
                .flatMap(o -> trips.findById(o.getTripId()).map(t -> mapper.toOffer(o, t)));
    }

    private void closeOffer(DriverOffer offer, DriverOffer.Status status, String reason, Instant now) {
        offer.close(status, now);
        if (!driverStates.releaseOffer(offer.getDriverId())) {
            log.warn("Driver {} was not OFFERED when closing offer {}", offer.getDriverId(), offer.getId());
        }
        events.publishEvent(new OfferClosed(offer.getId(), offer.getTripId(), offer.getDriverId(), reason));
    }

    private void publishStatusChanged(Trip trip, TripStatus old, Instant now) {
        events.publishEvent(new TripStatusChanged(trip.getId(), trip.getCode(), trip.getCustomerId(),
                trip.getDriverId(), old, trip.getStatus(), trip.getVersion(), now));
    }
}
