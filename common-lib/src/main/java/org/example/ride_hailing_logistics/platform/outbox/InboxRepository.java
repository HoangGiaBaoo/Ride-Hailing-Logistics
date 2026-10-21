package org.example.ride_hailing_logistics.platform.outbox;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InboxRepository extends JpaRepository<InboxEvent, UUID> {

    // Nhan lai cung event (stream gui lai sau su co) -> 0 dong, khong xu ly lan hai
    @Modifying
    @Query(value = """
            INSERT INTO inbox_events (id, aggregate_type, aggregate_id, event_type, event_version, payload, correlation_id,
                                      producer, occurred_at, received_at, status, attempts, next_attempt_at)
            VALUES (:id, :aggregateType, :aggregateId, :eventType, :eventVersion, :payload, :correlationId,
                    :producer, :occurredAt, :now, 'NEW', 0, :now)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("aggregateType") String aggregateType,
                       @Param("aggregateId") String aggregateId, @Param("eventType") String eventType,
                       @Param("eventVersion") int eventVersion, @Param("payload") String payload,
                       @Param("correlationId") String correlationId, @Param("producer") String producer,
                       @Param("occurredAt") Instant occurredAt, @Param("now") Instant now);

    default int receive(EventEnvelope e, Instant now) {
        return insertIfAbsent(e.eventId(), e.aggregateType(), e.aggregateId(), e.eventType(), e.eventVersion(),
                e.payload(), e.correlationId(), e.producer(), e.occurredAt(), now);
    }

    @Query(value = """
            SELECT * FROM inbox_events
            WHERE status = 'NEW' AND next_attempt_at <= :now
            ORDER BY occurred_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<InboxEvent> lockBatch(@Param("now") Instant now, @Param("limit") int limit);

    Page<InboxEvent> findByStatusOrderByOccurredAtDesc(OutboxStatus status, Pageable pageable);

    // Tra ve 1 neu lan dau xu ly cap (event, handler); 0 neu da xu ly roi
    @Modifying
    @Query(value = """
            INSERT INTO processed_events (event_id, handler, processed_at)
            VALUES (:eventId, :handler, :now)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int markHandled(@Param("eventId") UUID eventId, @Param("handler") String handler, @Param("now") Instant now);
}
