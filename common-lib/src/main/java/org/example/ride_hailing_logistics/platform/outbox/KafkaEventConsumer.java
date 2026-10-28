package org.example.ride_hailing_logistics.platform.outbox;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.example.ride_hailing_logistics.platform.kafka.KafkaConfig;
import org.example.ride_hailing_logistics.platform.kafka.KafkaTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Nghe moi topic event theo consumer group = ten service: moi service nhan du moi event, chi giu loai co handler.
 * Ghi vao inbox_events (trung eventId thi bo qua) roi moi ack offset. Chet giua ghi inbox va ack thi Kafka giao lai,
 * inbox loc trung. Xu ly nghiep vu chay sau o InboxProcessor de mot handler loi khong chan ca partition.
 */
@Component
public class KafkaEventConsumer {

    private final InboxRepository inbox;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Set<String> wantedTypes;

    public KafkaEventConsumer(InboxRepository inbox, ObjectMapper mapper, List<EventHandler> handlers,
                              PlatformTransactionManager txManager, Clock clock) {
        this.inbox = inbox;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.wantedTypes = handlers.stream().flatMap(h -> h.eventTypes().stream()).collect(Collectors.toUnmodifiableSet());
    }

    @KafkaListener(id = "${spring.application.name}-events", topicPattern = KafkaTopics.EVENT_TOPIC_PATTERN_EXPR,
            groupId = KafkaTopics.GROUP_EXPR, containerFactory = KafkaConfig.EVENT_LISTENER_FACTORY)
    public void onEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String type = header(record, KafkaTopics.HEADER_EVENT_TYPE);
        // Loc bang header truoc: khong ton cong parse JSON cho event service nay khong quan tam
        if (type != null && !wantedTypes.contains(type)) {
            ack.acknowledge();
            return;
        }
        // JSON hong -> nem loi khong thu lai -> ban tin sang topic .DLT
        EventEnvelope envelope = mapper.readValue(record.value(), EventEnvelope.class);
        if (wantedTypes.contains(envelope.eventType())) {
            tx.executeWithoutResult(status -> inbox.receive(envelope, clock.instant()));
        }
        ack.acknowledge();
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
