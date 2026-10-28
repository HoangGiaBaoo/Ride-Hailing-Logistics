package org.example.ride_hailing_logistics.pricing.repository;

import org.example.ride_hailing_logistics.pricing.entity.FareQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface FareQuoteRepository extends JpaRepository<FareQuote, UUID> {

    /**
     * Dung quote mot lan, nguyen tu: hai request tao chuyen cung quote thi chi mot request doi duoc ISSUED -> USED.
     * Kiem luon chu so huu va han dung (FR-PRI-014, FR-PRI-015, BR-005).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update FareQuote q set q.status = org.example.ride_hailing_logistics.pricing.entity.FareQuote.Status.USED, q.usedAt = :now,
                   q.consumedByTripId = :tripId
            where q.id = :id and q.customerId = :customerId
              and q.status = org.example.ride_hailing_logistics.pricing.entity.FareQuote.Status.ISSUED and q.expiresAt > :now
            """)
    int consume(@Param("id") UUID id, @Param("customerId") UUID customerId, @Param("tripId") UUID tripId,
                @Param("now") Instant now);

    /** Bu tru khi trip-service tao chuyen that bai sau khi da dung quote: tra quote ve ISSUED (con han thi dung lai duoc). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update FareQuote q set q.status = org.example.ride_hailing_logistics.pricing.entity.FareQuote.Status.ISSUED, q.usedAt = null,
                   q.consumedByTripId = null
            where q.id = :id and q.consumedByTripId = :tripId
              and q.status = org.example.ride_hailing_logistics.pricing.entity.FareQuote.Status.USED
            """)
    int release(@Param("id") UUID id, @Param("tripId") UUID tripId);
}
