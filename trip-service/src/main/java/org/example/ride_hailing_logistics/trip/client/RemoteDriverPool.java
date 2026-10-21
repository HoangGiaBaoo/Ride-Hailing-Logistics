package org.example.ride_hailing_logistics.trip.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
class RemoteDriverPool implements DriverPool {

    private final DriverClient client;

    @Override
    public List<DriverClient.Candidate> candidates(double lat, double lng, double radiusMeters, VehicleType vehicleType, int limit) {
        List<DriverClient.Candidate> found = client.candidates(lat, lng, radiusMeters, vehicleType, limit).data();
        return found == null ? List.of() : found;
    }

    @Override
    public boolean reserve(UUID driverId, UUID offerId) {
        return client.reserve(driverId, new DriverClient.HoldCommand(offerId)).data().success();
    }

    @Override
    public boolean markBusy(UUID driverId, UUID offerId, UUID tripId) {
        return client.markBusy(driverId, new DriverClient.TripHoldCommand(offerId, tripId)).data().success();
    }

    @Override
    public void revertBusy(UUID driverId, UUID tripId, UUID offerId) {
        bestEffort("revert-busy", driverId, () -> client.revertBusy(driverId, new DriverClient.TripHoldCommand(offerId, tripId)));
    }

    @Override
    public void releaseOffer(UUID driverId, UUID offerId) {
        bestEffort("release-offer", driverId, () -> client.releaseOffer(driverId, new DriverClient.HoldCommand(offerId)));
    }

    @Override
    public void finishTrip(UUID driverId, UUID tripId) {
        bestEffort("finish-trip", driverId, () -> client.finishTrip(driverId, new DriverClient.TripHoldCommand(null, tripId)));
    }

    @Override
    public DriverSnapshot snapshot(UUID driverId) {
        return client.snapshot(driverId).data();
    }

    @Override
    public Optional<DriverClient.Position> location(UUID driverId) {
        try {
            return Optional.ofNullable(client.location(driverId).data());
        } catch (BusinessException e) {
            if (e.getCode() == ErrorCode.RESOURCE_NOT_FOUND) return Optional.empty();
            throw e;
        }
    }

    private void bestEffort(String action, UUID driverId, Runnable call) {
        try {
            call.run();
        } catch (RuntimeException e) {
            log.warn("Driver {} {} failed, reconciler will retry: {}", driverId, action, e.toString());
        }
    }
}
