package org.example.ride_hailing_logistics.platform.outbox;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.web.CorrelationId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Giao event trong inbox cho cac handler cua service. Moi (event, handler) chay trong transaction rieng:
 * insert processed_events + hieu ung nghiep vu cung commit hoac cung rollback (FR-EVT-004).
 */
@Slf4j
@Component
public class InboxProcessor {

    private static final Duration LEASE = Duration.ofSeconds(30);

    private final InboxRepository repository;
    private final List<EventHandler> handlers;
    private final TransactionTemplate requiresNew;
    private final AppProperties.Outbox props;
    private final Clock clock;

    public InboxProcessor(InboxRepository repository, List<EventHandler> handlers, PlatformTransactionManager txManager,
                          AppProperties appProperties, Clock clock) {
        this.repository = repository;
        this.handlers = handlers;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.props = appProperties.outbox();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval}")
    public void poll() {
        if (handlers.isEmpty()) return;
        for (UUID id : claimBatch()) {
            process(id);
        }
    }

    private List<UUID> claimBatch() {
        List<UUID> ids = requiresNew.execute(status -> {
            Instant now = clock.instant();
            List<InboxEvent> events = repository.lockBatch(now, props.batchSize());
            events.forEach(e -> e.lease(now.plus(LEASE)));
            return events.stream().map(InboxEvent::getId).toList();
        });
        return ids == null ? List.of() : ids;
    }

    private void process(UUID eventId) {
        InboxEvent event = repository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxStatus.NEW) {
            return;
        }
        EventEnvelope envelope = event.toEnvelope();
        CorrelationId.with(envelope.correlationId(), () -> {
            String error = null;
            for (EventHandler handler : handlers) {
                if (!handler.eventTypes().contains(envelope.eventType())) continue;
                try {
                    requiresNew.executeWithoutResult(status -> {
                        int first = repository.markHandled(envelope.eventId(), handler.name(), clock.instant());
                        if (first == 1) {
                            handler.handle(envelope);
                        }
                    });
                } catch (RuntimeException ex) {
                    log.warn("Handler {} failed for event {} ({}): {}", handler.name(), envelope.eventId(),
                            envelope.eventType(), ex.toString());
                    error = handler.name() + ": " + ex;
                }
            }
            finish(eventId, error);
            return null;
        });
    }

    private void finish(UUID eventId, String error) {
        requiresNew.executeWithoutResult(status -> repository.findById(eventId).ifPresent(e -> {
            Instant now = clock.instant();
            if (error == null) {
                e.markProcessed(now);
            } else {
                long backoff = Math.min(300, 1L << Math.min(e.getAttempts(), 8));
                e.markFailedAttempt(error, now.plusSeconds(backoff), props.maxAttempts());
            }
        }));
    }
}
