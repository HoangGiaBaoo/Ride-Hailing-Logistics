package org.example.ride_hailing_logistics.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** De nghi chuyen gui mot tai xe (FR-MAT-007). */
@Entity
@Table(name = "driver_offers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverOffer {

    public enum Status { PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELLED }

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int round;

    @Column(name = "distance_meters", nullable = false)
    private int distanceMeters;

    @Column(name = "offered_at", nullable = false)
    private Instant offeredAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    public static DriverOffer create(UUID tripId, UUID driverId, int round, int distanceMeters, Instant now, Instant expiresAt) {
        DriverOffer o = new DriverOffer();
        o.id = UUID.randomUUID();
        o.tripId = tripId;
        o.driverId = driverId;
        o.status = Status.PENDING;
        o.round = round;
        o.distanceMeters = distanceMeters;
        o.offeredAt = now;
        o.expiresAt = expiresAt;
        return o;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void close(Status to, Instant now) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Offer " + id + " is not pending");
        }
        status = to;
        respondedAt = now;
    }
}
