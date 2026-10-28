package org.example.ride_hailing_logistics.common.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Chi ride-service giu ket noi WebSocket. Service khac (payment) muon day ban tin realtime thi phat len Redis Pub/Sub,
 * ride-service nghe va chuyen tiep toi dung phien. Pub/Sub khong luu: mat ban tin thi client lay snapshot
 * qua API (FR-RT-007) — dung cho du lieu song (thong bao), khong dung cho tien.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeBridge {

    /** Thong bao rieng cho mot nguoi dung (vd PAYMENT_UPDATED). */
    public static final String USER_NOTIFICATION_CHANNEL = "rt:user-notification";

    public static final String QUEUE_OFFERS = "/queue/offers";
    public static final String QUEUE_NOTIFICATIONS = "/queue/notifications";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public void notifyUser(UUID userId, String queue, String type, Object data) {
        publish(USER_NOTIFICATION_CHANNEL, new UserNotification(userId, queue, type, mapper.valueToTree(data)));
    }

    private void publish(String channel, Object message) {
        try {
            redis.convertAndSend(channel, mapper.writeValueAsString(message));
        } catch (RuntimeException e) {
            // Realtime khong duoc lam hong nghiep vu
            log.warn("Realtime publish to {} failed: {}", channel, e.toString());
        }
    }

    public record UserNotification(UUID userId, String queue, String type, tools.jackson.databind.JsonNode data) {}

}
