package org.example.ride_hailing_logistics.trip.service;

import org.example.ride_hailing_logistics.trip.dto.response.OfferResponse;
import org.example.ride_hailing_logistics.trip.entity.Trip;

import java.util.Optional;
import java.util.UUID;

/** Matching va offer (FR-MAT-001...015). */
public interface DispatchService {

    /** Tim ung vien va gui offer. Goi lap lai an toan: chuyen khong con MATCHING / dang co offer PENDING thi bo qua. */
    void dispatchNext(UUID tripId);

    /** Chap nhan offer nguyen tu (FR-MAT-010). Tra Trip da gan tai xe. */
    Trip accept(UUID offerId, UUID driverId);

    void decline(UUID offerId, UUID driverId);

    void expire(UUID offerId);

    /** Goi trong transaction huy chuyen, khi da khoa hang trip (FR-CAN-005). */
    void cancelOpenOffers(UUID tripId);

    Optional<OfferResponse> currentOfferFor(UUID driverId);
}
