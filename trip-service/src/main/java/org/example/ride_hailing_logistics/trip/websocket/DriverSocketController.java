package org.example.ride_hailing_logistics.trip.websocket;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.trip.client.LocationClient;
import org.example.ride_hailing_logistics.trip.dto.request.TelemetryRequest;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * Kenh telemetry chinh qua WebSocket (FR-LOC-001). driverId lay tu Principal cua phien, khong tu payload;
 * kiem hop le + ghi chi muc nam o location-service.
 */
@Controller
@RequiredArgsConstructor
public class DriverSocketController {

    private final LocationClient locationClient;
    private final RealtimePublisher publisher;

    @MessageMapping("/driver/location")
    public void location(@Payload TelemetryRequest telemetry, Principal principal) {
        UUID driverId = UUID.fromString(principal.getName());
        try {
            locationClient.ingest(driverId, telemetry);
        } catch (BusinessException e) {
            // Bao lai cho app tai xe de hien canh bao GPS yeu / dang Offline (UI-DRV-008)
            publisher.toUser(driverId, RealtimePublisher.QUEUE_NOTIFICATIONS, "TELEMETRY_REJECTED",
                    Map.of("code", e.getCode().name(), "message", e.getMessage()));
        }
    }
}
