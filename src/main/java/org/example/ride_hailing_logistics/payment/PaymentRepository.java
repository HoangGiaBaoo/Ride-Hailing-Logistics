package org.example.ride_hailing_logistics.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.providerRef = :ref")
    Optional<Payment> findByProviderRefForUpdate(@Param("ref") String ref);

    boolean existsByTripIdAndKind(UUID tripId, Payment.Kind kind);

    List<Payment> findByTripIdOrderByCreatedAt(UUID tripId);

    Page<Payment> findByStatus(Payment.Status status, Pageable pageable);

    @Query("select p.id from Payment p where p.status = :status and p.method = :method and p.providerRef is null and p.updatedAt < :before")
    List<UUID> findUnsubmitted(@Param("status") Payment.Status status,
                               @Param("method") org.example.ride_hailing_logistics.common.domain.PaymentMethod method,
                               @Param("before") Instant before);

    // Ghi nhan callback da xu ly; 0 = callback lap lai (chong replay)
    @Modifying
    @Query(value = "INSERT INTO payment_callback_events (event_id, received_at) VALUES (:id, :now) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int recordCallback(@Param("id") String eventId, @Param("now") Instant now);
}
