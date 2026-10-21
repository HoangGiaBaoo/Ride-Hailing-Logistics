package org.example.ride_hailing_logistics.platform.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Doc Redis Stream chung theo consumer group = ten service: moi service nhan du moi event mot lan,
 * chi giu lai loai event co handler, ghi vao inbox roi moi XACK.
 * Chet giua "ghi inbox" va "XACK" thi lan sau doc lai pending; inbox loc trung theo eventId.
 */
@Slf4j
@Component
public class EventStreamConsumer {

    private static final int BATCH = 100;

    private final StringRedisTemplate redis;
    private final InboxRepository inbox;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Set<String> wantedTypes;
    private final String group;
    private final Consumer consumer;
    // Lan doc dau tien sau khi khoi dong lay lai cac ban ghi da nhan nhung chua XACK (offset 0)
    private boolean drainPending = true;
    private boolean groupReady;

    public EventStreamConsumer(StringRedisTemplate redis, InboxRepository inbox, List<EventHandler> handlers,
                               PlatformTransactionManager txManager, Clock clock,
                               @Value("${spring.application.name}") String applicationName,
                               @Value("${server.port:8080}") String port) {
        this.redis = redis;
        this.inbox = inbox;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.wantedTypes = handlers.stream().flatMap(h -> h.eventTypes().stream()).collect(Collectors.toUnmodifiableSet());
        this.group = applicationName;
        // Ten consumer on dinh qua cac lan khoi dong de doc lai dung pending cua minh (gia dinh mot instance/cong)
        this.consumer = Consumer.from(applicationName, applicationName + "-" + port);
    }

    @Scheduled(fixedDelayString = "${app.outbox.consume-interval:100ms}")
    public void poll() {
        if (wantedTypes.isEmpty()) return;
        try {
            ensureGroup();
            ReadOffset offset = drainPending ? ReadOffset.from("0") : ReadOffset.lastConsumed();
            List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(consumer,
                    StreamReadOptions.empty().count(BATCH), StreamOffset.create(OutboxRelay.EventStream.KEY, offset));
            if (records == null || records.isEmpty()) {
                drainPending = false;
                return;
            }
            store(records);
            redis.opsForStream().acknowledge(OutboxRelay.EventStream.KEY, group,
                    records.stream().map(MapRecord::getId).toArray(RecordId[]::new));
        } catch (DataAccessException e) {
            // NOGROUP khi Redis bi xoa du lieu: tao lai group o lan sau
            groupReady = false;
            log.warn("Reading event stream failed: {}", e.getMostSpecificCause().getMessage());
        }
    }

    private void store(List<MapRecord<String, Object, Object>> records) {
        tx.executeWithoutResult(status -> {
            for (MapRecord<String, Object, Object> record : records) {
                Map<String, String> fields = new HashMap<>();
                record.getValue().forEach((k, v) -> fields.put(String.valueOf(k), String.valueOf(v)));
                if (!wantedTypes.contains(fields.get("eventType"))) continue;
                inbox.receive(OutboxRelay.EventStream.fromFields(fields), clock.instant());
            }
        });
    }

    private void ensureGroup() {
        if (groupReady) return;
        byte[] key = OutboxRelay.EventStream.KEY.getBytes(StandardCharsets.UTF_8);
        try {
            // Offset 0 + MKSTREAM: service khoi dong sau van nhan duoc event phat truoc do
            redis.execute((RedisCallback<String>) c -> c.streamCommands().xGroupCreate(key, group, ReadOffset.from("0"), true));
        } catch (DataAccessException e) {
            // Spring boc loi Redis: "BUSYGROUP" nam o cause, getMessage() chi la "Error in execution"
            if (!String.valueOf(e.getMostSpecificCause().getMessage()).contains("BUSYGROUP")) throw e;
        }
        groupReady = true;
        drainPending = true;
    }
}
