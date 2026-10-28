package org.example.ride_hailing_logistics.location.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.platform.kafka.KafkaTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Day vi tri moi len topic {ns}.location.updates (khoa = driverId -> cung partition, giu thu tu theo tai xe).
 * Khong qua outbox: vi tri la du lieu song, mat mot ban tin thi ban sau 3 giay thay the (NFR-PERF-001).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationEventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final KafkaTopics topics;
    private final ObjectMapper mapper;

    public void publish(DriverLocationUpdated update) {
        kafka.send(topics.locationUpdates(), update.driverId().toString(), mapper.writeValueAsString(update))
                .whenComplete((result, ex) -> {
                    if (ex != null) log.debug("Location update for driver dropped: {}", ex.toString());
                });
    }
}
