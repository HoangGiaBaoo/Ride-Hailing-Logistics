package org.example.ride_hailing_logistics.platform.outbox;

import java.time.Instant;
import java.util.UUID;

/** Envelope event lien mien (COM-009): eventId, eventType, eventVersion, occurredAt, correlationId, producer, payload. */
public record EventEnvelope(UUID eventId, String eventType, int eventVersion, Instant occurredAt, String correlationId,
                            String producer, String aggregateType, String aggregateId, String payload) {
}
