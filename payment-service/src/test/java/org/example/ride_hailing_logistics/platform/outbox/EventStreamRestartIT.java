package org.example.ride_hailing_logistics.platform.outbox;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Service khoi dong lai khi consumer group da ton tai tren Redis (XGROUP CREATE tra BUSYGROUP) van phai doc tiep.
 * Loi cu: BUSYGROUP nam o cause cua exception Spring nen bi coi la loi that -> consumer ket vinh vien.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class EventStreamRestartIT {

    private static final String PROBE = "restart-probe";

    @Autowired private StringRedisTemplate redis;
    @Autowired private InboxRepository inbox;
    @Autowired private List<EventHandler> handlers;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void consumerJoinsAnExistingGroupAfterRestart() {
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
        // Lan chay truoc da tao group
        redis.opsForStream().createGroup(OutboxRelay.EventStream.KEY, ReadOffset.from("0"), PROBE);
        UUID eventId = UUID.randomUUID();
        EventEnvelope event = new EventEnvelope(eventId, "DriverApproved", 1, Instant.now(), null, "driver", "Driver",
                UUID.randomUUID().toString(), "{\"driverId\":\"" + UUID.randomUUID() + "\"}");
        RecordId id = redis.opsForStream().add(StreamRecords.string(OutboxRelay.EventStream.toFields(event))
                .withStreamKey(OutboxRelay.EventStream.KEY));

        EventStreamConsumer restarted = new EventStreamConsumer(redis, inbox, handlers, txManager, Clock.systemUTC(), PROBE, "0");
        restarted.poll();   // drain pending (khong co)
        restarted.poll();

        String lastDelivered = redis.opsForStream().groups(OutboxRelay.EventStream.KEY).stream()
                .filter(g -> g.groupName().equals(PROBE)).findFirst().orElseThrow().lastDeliveredId();
        assertEquals(id.getValue(), lastDelivered, "consumer stuck on an existing group");
        assertEquals(1, jdbc.queryForObject("select count(*) from inbox_events where id = ?", Integer.class, eventId));
    }
}
