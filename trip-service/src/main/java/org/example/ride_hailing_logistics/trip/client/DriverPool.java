package org.example.ride_hailing_logistics.trip.client;

import org.example.ride_hailing_logistics.common.domain.VehicleType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Trang thai kha dung cua tai xe nam o driver-service. Moi lenh mang offer/chuyen dang giu tai xe
 * nen goi lai an toan va lenh cu den tre khong dong vao lan giu moi.
 */
public interface DriverPool {

    /** Tai xe AVAILABLE, du dieu kien, co vi tri moi gan diem don (da loc o driver-service). */
    List<DriverClient.Candidate> candidates(double lat, double lng, double radiusMeters, VehicleType vehicleType, int limit);

    /** AVAILABLE -> OFFERED cho offer nay. false = tai xe vua bi giu cho offer khac / vua Offline. */
    boolean reserve(UUID driverId, UUID offerId);

    /** OFFERED (offer) -> BUSY (chuyen). false = tai xe khong con duoc giu cho offer nay. */
    boolean markBusy(UUID driverId, UUID offerId, UUID tripId);

    /** Bu tru markBusy khi transaction chap nhan rollback. */
    void revertBusy(UUID driverId, UUID tripId, UUID offerId);

    /** Tra tai xe sau khi offer dong. Loi chi log: job doi soat cua driver-service lo phan con lai. */
    void releaseOffer(UUID driverId, UUID offerId);

    /** Tra tai xe sau khi chuyen ket thuc. Loi chi log nhu tren. */
    void finishTrip(UUID driverId, UUID tripId);

    DriverSnapshot snapshot(UUID driverId);

    Optional<DriverClient.Position> location(UUID driverId);
}
