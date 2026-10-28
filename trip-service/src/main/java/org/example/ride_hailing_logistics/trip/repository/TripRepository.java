package org.example.ride_hailing_logistics.trip.repository;

import jakarta.persistence.LockModeType;
import org.example.ride_hailing_logistics.trip.entity.Trip;
import org.example.ride_hailing_logistics.trip.enums.TripStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID>, JpaSpecificationExecutor<Trip> {

    /**
     * Khoa hang chuyen (SELECT ... FOR UPDATE). Moi thao tac doi trang thai chuyen — chap nhan offer, huy,
     * het han offer, dispatch — deu khoa o day truoc, nen chung xep hang thay vi chen ngang nhau (CON-06).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByCustomerIdAndStatusIn(UUID customerId, Collection<TripStatus> statuses);

    Page<Trip> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Trip> findByDriverId(UUID driverId, Pageable pageable);

    List<Trip> findByStatusIn(Collection<TripStatus> statuses);

    @Query("""
            select t.id from Trip t
            where t.status = :status and t.nextDispatchAt is not null and t.nextDispatchAt <= :now
            order by t.nextDispatchAt
            limit 50
            """)
    List<UUID> findDueForDispatch(@Param("status") TripStatus status, @Param("now") Instant now);
}
