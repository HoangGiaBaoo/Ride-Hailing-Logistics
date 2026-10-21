package org.example.ride_hailing_logistics.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gui ban tin realtime voi envelope {messageId, type, version, sentAt, sequence, data} (COM-007).
 * sequence tang dan theo tung kenh de client bo ban tin cu/trung (FR-RT-006).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimePublisher {

    public static final String QUEUE_OFFERS = "/queue/offers";
    public static final String QUEUE_NOTIFICATIONS = "/queue/notifications";

    private final SimpMessagingTemplate messaging;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final AtomicLong fallbackSequence = new AtomicLong(System.currentTimeMillis() * 1000);

    public void toTrip(UUID tripId, String type, Object data) {
        String destination = "/topic/trips/" + tripId;
        messaging.convertAndSend(destination, envelope(destination, type, data));
    }

    public void toUser(UUID userId, String queue, String type, Object data) {
        messaging.convertAndSendToUser(userId.toString(), queue, envelope("user:" + userId + queue, type, data));
    }

    private Envelope envelope(String channel, String type, Object data) {
        return new Envelope(UUID.randomUUID().toString(), type, 1, clock.instant(), nextSequence(channel), data);
    }

    private long nextSequence(String channel) {
        try {
            Long value = redis.opsForValue().increment("rt:seq:" + channel);
            if (value != null) return value;
        } catch (DataAccessException e) {
            log.debug("Redis sequence unavailable: {}", e.getMessage());
        }
        // Redis loi: van tang dan trong tien trinh nay; client ket noi lai se lay snapshot (FR-RT-005)
        return fallbackSequence.incrementAndGet();
    }

    public record Envelope(String messageId, String type, int version, Instant sentAt, long sequence, Object data) {}
}
