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

/**
 * Event cua service khac da nhan ve, cho handler cua service nay xu ly.
 * id = eventId goc nen nhan trung tu stream (gui lai, doc lai pending) chi con mot dong.
 */
@Entity
@Table(name = "inbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxEvent {

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

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

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
