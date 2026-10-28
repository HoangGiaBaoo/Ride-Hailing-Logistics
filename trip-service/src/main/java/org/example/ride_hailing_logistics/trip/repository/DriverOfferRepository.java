package org.example.ride_hailing_logistics.trip.repository;

import jakarta.persistence.LockModeType;
import org.example.ride_hailing_logistics.trip.entity.DriverOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverOfferRepository extends JpaRepository<DriverOffer, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from DriverOffer o where o.id = :id")
    Optional<DriverOffer> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Chi lay tripId (khong nap entity) de khoa hang chuyen TRUOC, roi moi nap offer bang FOR UPDATE.
     * Neu nap entity truoc khi khoa, Hibernate giu ban cu trong persistence context va bo qua du lieu moi.
     */
    @Query("select o.tripId from DriverOffer o where o.id = :id and o.driverId = :driverId")
    Optional<UUID> findTripIdForDriver(@Param("id") UUID id, @Param("driverId") UUID driverId);

    @Query("select o.tripId from DriverOffer o where o.id = :id")
    Optional<UUID> findTripId(@Param("id") UUID id);

    List<DriverOffer> findByTripId(UUID tripId);

    List<DriverOffer> findByTripIdAndStatus(UUID tripId, DriverOffer.Status status);

    Optional<DriverOffer> findFirstByDriverIdAndStatus(UUID driverId, DriverOffer.Status status);

    @Query("""
            select o.id from DriverOffer o
            where o.status = :status and o.expiresAt <= :now
            order by o.expiresAt
            limit 100
            """)
    List<UUID> findExpired(@Param("status") DriverOffer.Status status, @Param("now") Instant now);
}
