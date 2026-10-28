package org.example.ride_hailing_logistics.trip.event.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.platform.kafka.KafkaTopics;
import org.example.ride_hailing_logistics.trip.event.DriverLocationUpdated;
import org.example.ride_hailing_logistics.trip.repository.ActiveTripRegistry;
import org.example.ride_hailing_logistics.trip.websocket.RealtimePublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vi tri tai xe -> khach cua chuyen dang chay. Registry chi chua chuyen ACCEPTED...IN_TRIP, nen khach chua
 * duoc ghep / chuyen da xong khong nhan vi tri (FR-LOC-011, BR-013).
 * Group rieng, khong qua inbox: du lieu song, mat mot ban tin thi 3 giay sau co ban moi. Moi loi deu nuot
 * de khong day vi tri vao DLT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationUpdatesListener {

    private final ActiveTripRegistry activeTrips;
    private final RealtimePublisher publisher;
    private final ObjectMapper mapper;

    @KafkaListener(topics = KafkaTopics.LOCATION_UPDATES_EXPR, groupId = KafkaTopics.GROUP_EXPR + "-location")
    public void onLocation(String payload) {
        try {
            DriverLocationUpdated e = mapper.readValue(payload, DriverLocationUpdated.class);
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
        } catch (RuntimeException ex) {
            log.debug("Dropped location update: {}", ex.toString());
        }
    }
}
