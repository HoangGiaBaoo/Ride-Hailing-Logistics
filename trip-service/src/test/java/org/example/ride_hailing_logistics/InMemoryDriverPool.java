package org.example.ride_hailing_logistics;

import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.util.GeoUtils;
import org.example.ride_hailing_logistics.trip.client.DriverClient;
import org.example.ride_hailing_logistics.trip.client.DriverPool;
import org.example.ride_hailing_logistics.trip.client.DriverSnapshot;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ban gia driver-service cho integration test cua trip-service: cung ngu nghia cap nhat co dieu kien theo ref
 * (DriverProfileRepository.hold/moveHeld/releaseHeld), nguyen tu nho ConcurrentHashMap.compute.
 * Ban that duoc kiem rieng o DriverHoldIT cua driver-service tren PostgreSQL.
 */
public class InMemoryDriverPool implements DriverPool {

    public enum State { OFFLINE, AVAILABLE, OFFERED, BUSY }

    public record Hold(State state, UUID ref) {}

    private record Spot(double lat, double lng) {}

    private final Map<UUID, Hold> holds = new ConcurrentHashMap<>();
    private final Map<UUID, Spot> spots = new ConcurrentHashMap<>();

    public void reset() {
        holds.clear();
        spots.clear();
    }

    public void addAvailable(UUID driverId, double lat, double lng) {
        holds.put(driverId, new Hold(State.AVAILABLE, null));
        spots.put(driverId, new Spot(lat, lng));
    }

    public State stateOf(UUID driverId) {
        return holds.get(driverId).state();
    }

    @Override
    public List<DriverClient.Candidate> candidates(double lat, double lng, double radiusMeters, VehicleType vehicleType, int limit) {
        return spots.entrySet().stream()
                .filter(e -> holds.get(e.getKey()).state() == State.AVAILABLE)
                .map(e -> new DriverClient.Candidate(e.getKey(),
                        GeoUtils.haversineMeters(lat, lng, e.getValue().lat(), e.getValue().lng()), 1_000))
                .filter(c -> c.distanceMeters() <= radiusMeters)
                .sorted(Comparator.comparingDouble(DriverClient.Candidate::distanceMeters))
                .limit(limit)
                .toList();
    }

    @Override
    public boolean reserve(UUID driverId, UUID offerId) {
        return move(driverId, State.AVAILABLE, null, State.OFFERED, offerId) || is(driverId, State.OFFERED, offerId);
    }

    @Override
    public boolean markBusy(UUID driverId, UUID offerId, UUID tripId) {
        return move(driverId, State.OFFERED, offerId, State.BUSY, tripId) || is(driverId, State.BUSY, tripId);
    }

    @Override
    public void revertBusy(UUID driverId, UUID tripId, UUID offerId) {
        move(driverId, State.BUSY, tripId, State.OFFERED, offerId);
    }

    @Override
    public void releaseOffer(UUID driverId, UUID offerId) {
        move(driverId, State.OFFERED, offerId, State.AVAILABLE, null);
    }

    @Override
    public void finishTrip(UUID driverId, UUID tripId) {
        move(driverId, State.BUSY, tripId, State.AVAILABLE, null);
    }

    @Override
    public DriverSnapshot snapshot(UUID driverId) {
        return new DriverSnapshot(driverId, "Tài xế test", "+84930000000", UUID.nameUUIDFromBytes(driverId.toString().getBytes()),
                VehicleType.MOTORBIKE, "59X00000", "Honda Wave");
    }

    @Override
    public Optional<DriverClient.Position> location(UUID driverId) {
        return Optional.ofNullable(spots.get(driverId)).map(s -> new DriverClient.Position(s.lat(), s.lng()));
    }

    private boolean move(UUID driverId, State from, UUID expectedRef, State to, UUID newRef) {
        boolean[] moved = {false};
        holds.computeIfPresent(driverId, (id, h) -> {
            // Trang thai AVAILABLE khong co ref; cac trang thai giu phai dung ref moi duoc doi
            if (h.state() == from && (from == State.AVAILABLE || Objects.equals(h.ref(), expectedRef))) {
                moved[0] = true;
                return new Hold(to, newRef);
            }
            return h;
        });
        return moved[0];
    }

    private boolean is(UUID driverId, State state, UUID ref) {
        Hold h = holds.get(driverId);
        return h != null && h.state() == state && Objects.equals(h.ref(), ref);
    }
}
