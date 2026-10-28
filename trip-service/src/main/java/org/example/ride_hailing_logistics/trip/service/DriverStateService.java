package org.example.ride_hailing_logistics.trip.service;

import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.trip.dto.response.AvailabilityResponse;
import org.example.ride_hailing_logistics.trip.entity.DriverState;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Kha dung cua tai xe (SRS §7.2). Cac buoc reserve/markBusy/release chay TRONG transaction cua dispatch/chap nhan
 * offer (propagation REQUIRED), nen trip, offer va tai xe doi cung nhau hoac khong doi gi.
 */
public interface DriverStateService {

    AvailabilityResponse goOnline(UUID driverId);

    AvailabilityResponse goOffline(UUID driverId);

    AvailabilityResponse status(UUID driverId);

    Set<UUID> filterEligible(Collection<UUID> driverIds, VehicleType vehicleType);

    /** AVAILABLE -> OFFERED. false = vua bi giu cho chuyen khac hoac vua Offline. */
    boolean reserve(UUID driverId);

    /** OFFERED -> AVAILABLE (hoac OFFLINE neu bi dinh chi trong luc do). */
    boolean releaseOffer(UUID driverId);

    /** OFFERED -> BUSY khi chap nhan offer. */
    boolean markBusy(UUID driverId);

    /** BUSY -> AVAILABLE/OFFLINE khi chuyen ket thuc hoac bi huy. */
    boolean finishTrip(UUID driverId);

    DriverState require(UUID driverId);

    void applySuspension(UUID driverId, boolean suspended);

    void applyAccountStatus(UUID driverId, boolean disabled);

    void applyProfileName(UUID driverId, String fullName);
}
