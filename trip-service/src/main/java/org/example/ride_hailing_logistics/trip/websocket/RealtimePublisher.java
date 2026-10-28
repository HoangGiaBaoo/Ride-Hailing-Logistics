package org.example.ride_hailing_logistics.trip.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gui ban tin realtime voi envelope {messageId, type, version, sentAt, sequence, data} (COM-007).
 * Chay nhieu instance trip-service: khach co the ket noi WebSocket vao instance khac instance xu ly chuyen.
 * Vi vay moi ban tin phat qua Redis Pub/Sub (rt:fanout); moi instance nghe va gui cho phien cua minh.
 * sequence tang dan theo kenh (Redis INCR) de client bo ban tin cu/trung (FR-RT-006).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimePublisher {

    public static final String QUEUE_OFFERS = "/queue/offers";
    public static final String QUEUE_NOTIFICATIONS = "/queue/notifications";
    public static final String FANOUT_CHANNEL = "rt:fanout";

    private final SimpMessagingTemplate messaging;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final AtomicLong fallbackSequence = new AtomicLong(System.currentTimeMillis() * 1000);

    public void toTrip(UUID tripId, String type, Object data) {
        String destination = "/topic/trips/" + tripId;
        fanOut(new Delivery(null, destination, envelope(destination, type, data)));
    }

    public void toUser(UUID userId, String queue, String type, Object data) {
        fanOut(new Delivery(userId.toString(), queue, envelope("user:" + userId + queue, type, data)));
    }

    /** Goi tu listener Redis tren MOI instance: chi gui cho phien dang noi vao instance nay. */
    public void deliverLocally(Delivery delivery) {
        if (delivery.user() == null) {
            messaging.convertAndSend(delivery.destination(), delivery.envelope());
        } else {
            messaging.convertAndSendToUser(delivery.user(), delivery.destination(), delivery.envelope());
        }
    }

    private void fanOut(Delivery delivery) {
        try {
            redis.convertAndSend(FANOUT_CHANNEL, mapper.writeValueAsString(delivery));
        } catch (RuntimeException e) {
            // Redis loi: it nhat phien noi vao instance nay van nhan duoc
            log.debug("Realtime fan-out unavailable: {}", e.getMessage());
            deliverLocally(delivery);
        }
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

    /** user = null: gui vao topic chung; khac null: gui vao hang doi rieng cua nguoi do. */
    public record Delivery(String user, String destination, Envelope envelope) {}
}
