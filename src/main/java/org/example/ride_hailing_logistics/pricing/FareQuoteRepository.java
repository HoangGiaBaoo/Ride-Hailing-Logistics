package org.example.ride_hailing_logistics.pricing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface FareQuoteRepository extends JpaRepository<FareQuote, UUID> {

    @Modifying(flushAutomatically = true)
    @Query("""
            update FareQuote q set q.status = :to, q.usedAt = :now
            where q.id = :id and q.customerId = :customerId and q.status = :from and q.expiresAt > :now
            """)
    int transition(@Param("id") UUID id, @Param("customerId") UUID customerId, @Param("from") FareQuote.Status from,
                   @Param("to") FareQuote.Status to, @Param("now") Instant now);

    /**
     * Dung quote mot lan, nguyen tu: hai request tao chuyen cung quote thi chi mot request doi duoc ISSUED -> USED.
     * Kiem luon chu so huu va han dung (FR-PRI-014, FR-PRI-015, BR-005).
     */
    default int consume(UUID id, UUID customerId, Instant now) {
        return transition(id, customerId, FareQuote.Status.ISSUED, FareQuote.Status.USED, now);
    }
}
