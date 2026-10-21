package org.example.ride_hailing_logistics.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.realtime.RealtimeBridge;
import org.example.ride_hailing_logistics.common.realtime.RealtimeBridge.DriverLocationMessage;
import org.example.ride_hailing_logistics.common.realtime.RealtimeBridge.UserNotification;
import org.example.ride_hailing_logistics.trip.ActiveTripRegistry;
import org.example.ride_hailing_logistics.trip.TripLocalEvents;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cau noi sang WebSocket. Event cua chuyen chay SAU COMMIT: client khong bao gio thay trang thai chua duoc luu
 * (NFR-REL-001). Vi tri tai xe va thong bao tu service khac den qua Redis Pub/Sub (RealtimeBridge).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
class RealtimeListeners {

    private final RealtimePublisher publisher;
    private final ActiveTripRegistry activeTrips;
    private final ObjectMapper mapper;

    @Bean
    RedisMessageListenerContainer realtimeBridgeListener(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> safely(() ->
                        onDriverLocation(mapper.readValue(body(message.getBody()), DriverLocationMessage.class))),
                new ChannelTopic(RealtimeBridge.DRIVER_LOCATION_CHANNEL));
        container.addMessageListener((message, pattern) -> safely(() ->
                        onUserNotification(mapper.readValue(body(message.getBody()), UserNotification.class))),
                new ChannelTopic(RealtimeBridge.USER_NOTIFICATION_CHANNEL));
        return container;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTripStatus(TripLocalEvents.TripStatusChanged e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tripId", e.tripId());
        data.put("code", e.code());
        data.put("oldStatus", e.oldStatus());
        data.put("newStatus", e.newStatus());
        data.put("newStatusDisplay", e.newStatus().getDisplayName());
        data.put("tripVersion", e.version());
        data.put("occurredAt", e.occurredAt());
        safely(() -> {
            publisher.toTrip(e.tripId(), "TRIP_STATUS_CHANGED", data);
            // Kenh rieng: khach biet NO_DRIVER / tai xe biet chuyen bi huy ngay ca khi chua subscribe kenh chuyen (FR-RT-002)
            publisher.toUser(e.customerId(), RealtimePublisher.QUEUE_NOTIFICATIONS, "TRIP_STATUS_CHANGED", data);
            if (e.driverId() != null) {
                publisher.toUser(e.driverId(), RealtimePublisher.QUEUE_NOTIFICATIONS, "TRIP_STATUS_CHANGED", data);
            }
        });
    }

    /**
     * Vi tri tai xe -> khach cua chuyen dang chay. Registry chi chua chuyen ACCEPTED...IN_TRIP,
     * nen khach chua duoc ghep / chuyen da xong khong nhan vi tri (FR-LOC-011, BR-013).
     */
    void onDriverLocation(DriverLocationMessage e) {
        activeTrips.tripOf(e.driverId()).ifPresent(tripId -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("driverId", e.driverId());
            data.put("latitude", e.latitude());
            data.put("longitude", e.longitude());
            data.put("accuracyMeters", e.accuracyMeters());
            data.put("headingDegrees", e.heading());
            data.put("speedMetersPerSecond", e.speed());
            data.put("deviceTimestamp", e.deviceTimestamp());
            data.put("serverTimestamp", e.serverTimestamp());
            publisher.toTrip(tripId, "DRIVER_LOCATION_UPDATED", data);
        });
    }

    /** Thong bao rieng do service khac gui (vd PAYMENT_UPDATED tu payment-service). */
    void onUserNotification(UserNotification n) {
        if (!RealtimePublisher.QUEUE_NOTIFICATIONS.equals(n.queue()) && !RealtimePublisher.QUEUE_OFFERS.equals(n.queue())) {
            return;   // chi chuyen tiep toi hang doi rieng da biet, khong de service khac day vao topic tuy y
        }
        publisher.toUser(n.userId(), n.queue(), n.type(), n.data());
    }

    /** Offer chi gui dung phien tai xe duoc chon, khong phat rong (FR-MAT-008). Format theo Phu luc A.2. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOfferCreated(TripLocalEvents.OfferCreated e) {
        Map<String, Object> pickup = new LinkedHashMap<>();
        pickup.put("latitude", e.pickupLat());
        pickup.put("longitude", e.pickupLng());
        pickup.put("displayAddress", e.pickupAddress());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("offerId", e.offerId());
        data.put("tripId", e.tripId());
        data.put("serviceType", e.serviceType());
        data.put("pickup", pickup);
        data.put("dropoffAddress", e.dropoffAddress());
        data.put("estimatedPickupDistanceMeters", e.estimatedPickupDistanceMeters());
        data.put("estimatedFare", e.estimatedFare());
        data.put("currency", e.currency());
        data.put("expiresAt", e.expiresAt());
        safely(() -> publisher.toUser(e.driverId(), RealtimePublisher.QUEUE_OFFERS, "DRIVER_OFFER_CREATED", data));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOfferClosed(TripLocalEvents.OfferClosed e) {
        safely(() -> publisher.toUser(e.driverId(), RealtimePublisher.QUEUE_OFFERS, "DRIVER_OFFER_CLOSED",
                Map.of("offerId", e.offerId(), "tripId", e.tripId(), "reason", e.reason())));
    }

    private static String body(byte[] raw) {
        return new String(raw, StandardCharsets.UTF_8);
    }

    private void safely(Runnable send) {
        try {
            send.run();
        } catch (RuntimeException ex) {
            // Gui realtime that bai khong duoc lam hong nghiep vu; client lay lai snapshot qua API (FR-RT-007)
            log.warn("Realtime publish failed: {}", ex.toString());
        }
    }
}
