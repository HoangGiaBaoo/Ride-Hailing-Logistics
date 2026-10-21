package org.example.ride_hailing_logistics.platform.outbox;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Day event tu bang outbox cua service nay sang Redis Stream chung {@link EventStream#KEY}.
 * Event chi duoc danh dau PROCESSED sau khi XADD thanh cong: service chet giua chung thi lan sau gui lai,
 * phia nhan loc trung bang eventId (at-least-once + consumer idempotent = hieu ung dung mot lan).
 */
@Slf4j
@Component
public class OutboxRelay {

    // Thoi gian "giu cho": event da lay ra se khong bi relay khac lay lai trong khoang nay
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final long STREAM_MAX_LENGTH = 100_000;

    private final OutboxRepository repository;
    private final StringRedisTemplate redis;
    private final TransactionTemplate requiresNew;
    private final AppProperties.Outbox props;
    private final Clock clock;
    private long publishedSinceTrim;

    public OutboxRelay(OutboxRepository repository, StringRedisTemplate redis, PlatformTransactionManager txManager,
                       AppProperties appProperties, Clock clock) {
        this.repository = repository;
        this.redis = redis;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.props = appProperties.outbox();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval}")
    public void poll() {
        for (UUID id : claimBatch()) {
            publish(id);
        }
        if (publishedSinceTrim > 1_000) {
            // Stream la kenh van chuyen, khong phai kho luu: nguon su that van la bang outbox/inbox o moi service
            redis.opsForStream().trim(EventStream.KEY, STREAM_MAX_LENGTH, true);
            publishedSinceTrim = 0;
        }
    }

    private List<UUID> claimBatch() {
        List<UUID> ids = requiresNew.execute(status -> {
            Instant now = clock.instant();
            List<OutboxEvent> events = repository.lockBatch(now, props.batchSize());
            events.forEach(e -> e.lease(now.plus(LEASE)));
            return events.stream().map(OutboxEvent::getId).toList();
        });
        return ids == null ? List.of() : ids;
    }

    private void publish(UUID eventId) {
        OutboxEvent event = repository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxStatus.NEW) {
            return;
        }
        String error = null;
        try {
            redis.opsForStream().add(StreamRecords.newRecord().in(EventStream.KEY).ofMap(EventStream.toFields(event.toEnvelope())));
            publishedSinceTrim++;
        } catch (RuntimeException ex) {
            log.warn("Publishing event {} ({}) failed: {}", eventId, event.getEventType(), ex.toString());
            error = ex.toString();
        }
        finish(eventId, error);
    }

    private void finish(UUID eventId, String error) {
        requiresNew.executeWithoutResult(status -> repository.findById(eventId).ifPresent(e -> {
            Instant now = clock.instant();
            if (error == null) {
                e.markProcessed(now);
            } else {
                // Lui thoi gian thu lai theo cap so nhan: 1s, 2s, 4s ... toi da 5 phut
                long backoff = Math.min(300, 1L << Math.min(e.getAttempts(), 8));
                e.markFailedAttempt(error, now.plusSeconds(backoff), props.maxAttempts());
            }
        }));
    }

    /** Dinh dang ban ghi tren stream: moi truong cua envelope (COM-009) la mot field chuoi. */
    public static final class EventStream {

        public static final String KEY = "rhl:events";

        private EventStream() {
        }

        static Map<String, String> toFields(EventEnvelope e) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("eventId", e.eventId().toString());
            fields.put("eventType", e.eventType());
            fields.put("eventVersion", String.valueOf(e.eventVersion()));
            fields.put("occurredAt", e.occurredAt().toString());
            fields.put("correlationId", e.correlationId() == null ? "" : e.correlationId());
            fields.put("producer", e.producer());
            fields.put("aggregateType", e.aggregateType());
            fields.put("aggregateId", e.aggregateId());
            fields.put("payload", e.payload());
            return fields;
        }

        static EventEnvelope fromFields(Map<String, String> f) {
            String correlationId = f.get("correlationId");
            return new EventEnvelope(UUID.fromString(f.get("eventId")), f.get("eventType"),
                    Integer.parseInt(f.get("eventVersion")), Instant.parse(f.get("occurredAt")),
                    correlationId == null || correlationId.isEmpty() ? null : correlationId, f.get("producer"),
                    f.get("aggregateType"), f.get("aggregateId"), f.get("payload"));
        }
    }
}
