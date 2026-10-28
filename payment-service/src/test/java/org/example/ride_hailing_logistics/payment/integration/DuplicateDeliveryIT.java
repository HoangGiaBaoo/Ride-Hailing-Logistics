package org.example.ride_hailing_logistics.payment.integration;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.example.ride_hailing_logistics.platform.kafka.KafkaTopics;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Kafka giao it nhat mot lan: cung mot event (cung eventId) co the den hai lan — producer gui lai sau timeout,
 * hoac consumer chet truoc khi commit offset. Consumer phai idempotent: mot dong inbox, mot vi (FR-EVT-005).
 */
class DuplicateDeliveryIT extends PaymentIntegrationTest {

    @Autowired private KafkaTemplate<String, String> kafka;
    @Autowired private KafkaTopics topics;
    @Autowired private ObjectMapper mapper;

    @Test
    void sameEventDeliveredTwiceOpensOneWallet() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(eventId, "DriverApproved", 1, Instant.now(), null, "driver", "Driver",
                driverId.toString(), "{\"driverId\":\"" + driverId + "\",\"approvedAt\":\"" + Instant.now() + "\"}");
        String json = mapper.writeValueAsString(envelope);
        for (int i = 0; i < 2; i++) {
            var record = new ProducerRecord<>(topics.events("driver"), driverId.toString(), json);
            record.headers().add(KafkaTopics.HEADER_EVENT_TYPE, "DriverApproved".getBytes(StandardCharsets.UTF_8));
            record.headers().add(KafkaTopics.HEADER_EVENT_ID, eventId.toString().getBytes(StandardCharsets.UTF_8));
            kafka.send(record).get();
        }

        UUID processed = await(() -> {
            Integer n = jdbc.queryForObject("select count(*) from processed_events where event_id = ?", Integer.class, eventId);
            return n != null && n > 0 ? eventId : null;
        }, 30_000);
        assertNotNull(processed, "event was never processed");
        Thread.sleep(1_000);   // cho ban sao thu hai chac chan da di het duong ong
        assertEquals(1, jdbc.queryForObject("select count(*) from wallets where driver_id = ?", Integer.class, driverId));
        assertEquals(1, jdbc.queryForObject("select count(*) from inbox_events where id = ?", Integer.class, eventId));
    }
}
