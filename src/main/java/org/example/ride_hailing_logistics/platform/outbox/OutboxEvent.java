package org.example.ride_hailing_logistics.platform.outbox;

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

/** Event cho phat, ghi cung transaction voi thay doi nghiep vu (FR-EVT-002). Envelope theo COM-009. */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(nullable = false, length = 50)
    private String producer;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "processed_at")
    private Instant processedAt;

    static OutboxEvent create(String producer, String aggregateType, String aggregateId, String eventType,
                              int eventVersion, String payload, String correlationId, Instant now) {
        OutboxEvent e = new OutboxEvent();
        e.id = UUID.randomUUID();
        e.producer = producer;
        e.aggregateType = aggregateType;
        e.aggregateId = aggregateId;
        e.eventType = eventType;
        e.eventVersion = eventVersion;
        e.payload = payload;
        e.correlationId = correlationId;
        e.occurredAt = now;
        e.status = OutboxStatus.NEW;
        e.nextAttemptAt = now;
        return e;
    }

    void markProcessed(Instant now) {
        status = OutboxStatus.PROCESSED;
        processedAt = now;
        lastError = null;
    }

    void markFailedAttempt(String error, Instant nextAttempt, int maxAttempts) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        if (attempts >= maxAttempts) {
            // Qua so lan thu: dua vao "DLQ" de nguoi co quyen xu ly lai, khong thu vo han (FR-EVT-005, FR-EVT-008)
            status = OutboxStatus.FAILED;
        } else {
            nextAttemptAt = nextAttempt;
        }
    }

    void requeue(Instant now) {
        status = OutboxStatus.NEW;
        attempts = 0;
        nextAttemptAt = now;
    }

    void lease(Instant until) {
        nextAttemptAt = until;
    }

    public EventEnvelope toEnvelope() {
        return new EventEnvelope(id, eventType, eventVersion, occurredAt, correlationId, producer,
                aggregateType, aggregateId, payload);
    }
}
