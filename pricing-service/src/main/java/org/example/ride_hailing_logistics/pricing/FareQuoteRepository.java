package org.example.ride_hailing_logistics.pricing;

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
    @Modifying(flushAutomatically = true)
    @Query("""
            update FareQuote q set q.status = :used, q.usedAt = :now, q.usedByTrip = :tripId
            where q.id = :id and q.customerId = :customerId and q.status = :issued and q.expiresAt > :now
            """)
    int consume(@Param("id") UUID id, @Param("customerId") UUID customerId, @Param("tripId") UUID tripId,
                @Param("issued") FareQuote.Status issued, @Param("used") FareQuote.Status used, @Param("now") Instant now);

    default int consume(UUID id, UUID customerId, UUID tripId, Instant now) {
        return consume(id, customerId, tripId, FareQuote.Status.ISSUED, FareQuote.Status.USED, now);
    }

    /** Bu tru khi trip-service khong tao duoc chuyen sau khi da dung quote: chi chuyen da dung moi tra duoc. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update FareQuote q set q.status = :issued, q.usedAt = null, q.usedByTrip = null
            where q.id = :id and q.status = :used and q.usedByTrip = :tripId
            """)
    int release(@Param("id") UUID id, @Param("tripId") UUID tripId,
                @Param("issued") FareQuote.Status issued, @Param("used") FareQuote.Status used);
}
