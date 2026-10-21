package org.example.ride_hailing_logistics.platform.outbox;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    // SKIP LOCKED: nhieu relay (nhieu instance) cung chay thi moi event chi mot relay lay
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'NEW' AND next_attempt_at <= :now
            ORDER BY occurred_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockBatch(@Param("now") Instant now, @Param("limit") int limit);

    Page<OutboxEvent> findByStatusOrderByOccurredAtDesc(OutboxStatus status, Pageable pageable);
}
