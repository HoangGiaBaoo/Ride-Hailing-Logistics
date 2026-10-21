package org.example.ride_hailing_logistics.platform.outbox;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.web.CorrelationId;
import org.example.ride_hailing_logistics.platform.audit.AuditService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository repository;
    private final InboxRepository inbox;
    private final ObjectMapper mapper;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Bat buoc goi trong transaction nghiep vu dang mo: hoac ca thay doi lan event cung duoc luu, hoac khong cai nao.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(String producer, String aggregateType, Object aggregateId, String eventType, int version, Object payload) {
        OutboxEvent event = OutboxEvent.create(producer, aggregateType, String.valueOf(aggregateId), eventType, version,
                mapper.writeValueAsString(payload), CorrelationId.current(), clock.instant());
        repository.save(event);
        return event.getId();
    }

    public <T> T read(EventEnvelope event, Class<T> type) {
        return mapper.readValue(event.payload(), type);
    }

    /** Event khong gui duoc len stream sau so lan thu cho phep. */
    @Transactional(readOnly = true)
    public PageResponse<EventEnvelopeView> failedOutgoing(Pageable pageable) {
        return PageResponse.of(repository.findByStatusOrderByOccurredAtDesc(OutboxStatus.FAILED, pageable),
                e -> new EventEnvelopeView(e.getId(), e.getEventType(), e.getProducer(), e.getAggregateType(),
                        e.getAggregateId(), e.getAttempts(), e.getLastError(), e.getOccurredAt()));
    }

    /** Event nhan tu service khac ma handler cua service nay xu ly that bai qua so lan cho phep (FR-EVT-008). */
    @Transactional(readOnly = true)
    public PageResponse<EventEnvelopeView> failedIncoming(Pageable pageable) {
        return PageResponse.of(inbox.findByStatusOrderByOccurredAtDesc(OutboxStatus.FAILED, pageable),
                e -> new EventEnvelopeView(e.getId(), e.getEventType(), e.getProducer(), e.getAggregateType(),
                        e.getAggregateId(), e.getAttempts(), e.getLastError(), e.getOccurredAt()));
    }

    @Transactional
    public void retry(UUID eventId, Actor actor) {
        Instant now = clock.instant();
        String type = repository.findById(eventId).filter(e -> e.getStatus() == OutboxStatus.FAILED)
                .map(e -> {
                    e.requeue(now);
                    return e.getEventType();
                })
                .or(() -> inbox.findById(eventId).filter(e -> e.getStatus() == OutboxStatus.FAILED).map(e -> {
                    e.requeue(now);
                    return e.getEventType();
                }))
                .orElseThrow(() -> BusinessException.notFound("event lỗi"));
        auditService.record(actor, "EVENT_RETRY", "Event", eventId, Map.of("eventType", type));
    }

    public record EventEnvelopeView(UUID eventId, String eventType, String producer, String aggregateType,
                                    String aggregateId, int attempts, String lastError, Instant occurredAt) {}
}
