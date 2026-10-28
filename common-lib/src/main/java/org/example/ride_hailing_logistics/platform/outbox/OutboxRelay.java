package org.example.ride_hailing_logistics.platform.outbox;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.platform.kafka.KafkaTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Transactional Outbox: day event tu bang outbox_events len topic Kafka "rhl.{producer}.events", khoa = aggregateId.
 * Chi danh dau PROCESSED sau khi broker xac nhan (acks=all, producer idempotent): service chet giua chung thi
 * lan sau gui lai, phia nhan loc trung bang eventId (at-least-once + consumer idempotent = hieu ung dung mot lan).
 */
@Slf4j
@Component
public class OutboxRelay {

    // Thoi gian "giu cho": event da lay ra se khong bi relay khac lay lai trong khoang nay
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final KafkaTopics topics;
    private final ObjectMapper mapper;
    private final TransactionTemplate requiresNew;
    private final AppProperties.Outbox props;
    private final Clock clock;

    public OutboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka, KafkaTopics topics, ObjectMapper mapper,
                       PlatformTransactionManager txManager, AppProperties appProperties, Clock clock) {
        this.repository = repository;
        this.kafka = kafka;
        this.topics = topics;
        this.mapper = mapper;
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
            EventEnvelope envelope = event.toEnvelope();
            var record = new ProducerRecord<>(topics.events(envelope.producer()), envelope.aggregateId(),
                    mapper.writeValueAsString(envelope));
            record.headers().add(KafkaTopics.HEADER_EVENT_TYPE, bytes(envelope.eventType()));
            record.headers().add(KafkaTopics.HEADER_EVENT_ID, bytes(envelope.eventId().toString()));
            if (envelope.correlationId() != null) {
                record.headers().add(KafkaTopics.HEADER_CORRELATION_ID, bytes(envelope.correlationId()));
            }
            // Doi broker xac nhan roi moi danh dau da gui: danh dau truoc ma broker loi la mat event
            kafka.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            error = ex.toString();
        } catch (Exception ex) {
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

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
