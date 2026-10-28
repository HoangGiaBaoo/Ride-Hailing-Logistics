package org.example.ride_hailing_logistics.trip.service;

import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.trip.dto.request.CreateTripRequest;
import org.example.ride_hailing_logistics.trip.dto.request.TransitionRequest;
import org.example.ride_hailing_logistics.trip.dto.response.CancellationPreviewResponse;
import org.example.ride_hailing_logistics.trip.dto.response.HistoryEntryResponse;
import org.example.ride_hailing_logistics.trip.dto.response.TripResponse;
import org.example.ride_hailing_logistics.trip.enums.CancelReason;
import org.example.ride_hailing_logistics.trip.enums.TripStatus;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Vong doi chuyen (UC-03, UC-04, UC-08). Moi doi trang thai di qua Trip.transitionTo + TripStateMachine. */
public interface TripService {

    TripResponse create(UUID customerId, String idempotencyKey, CreateTripRequest request);

    TripResponse get(UUID tripId, Actor actor);

    List<HistoryEntryResponse> history(UUID tripId, Actor actor);

    PageResponse<TripResponse> mine(Actor actor, boolean asDriver, Pageable pageable);

    PageResponse<TripResponse> search(String code, UUID customerId, UUID driverId, ServiceType serviceType,
                                      TripStatus status, Instant from, Instant to, Pageable pageable);

    TripResponse startPickup(UUID tripId, Actor driver, TransitionRequest request);

    TripResponse arrive(UUID tripId, Actor driver, TransitionRequest request);

    TripResponse start(UUID tripId, Actor driver, TransitionRequest request);

    TripResponse complete(UUID tripId, Actor driver, TransitionRequest request);

    CancellationPreviewResponse previewCancellation(UUID tripId, Actor actor);

    TripResponse cancel(UUID tripId, Actor actor, CancelReason reason, String note, boolean staffException);

    void recordFinalFare(UUID tripId, BigDecimal amount);
}
