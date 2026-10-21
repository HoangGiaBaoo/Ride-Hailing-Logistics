package org.example.ride_hailing_logistics.realtime;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.trip.client.DriverClient;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * Kenh telemetry chinh qua WebSocket (FR-LOC-001). driverId lay tu Principal cua phien, khong tu payload.
 * Ket noi WS nam o trip-service nen ban tin duoc chuyen tiep sang driver-service (noi giu chi muc vi tri).
 */
@Controller
@RequiredArgsConstructor
public class DriverSocketController {

    private final DriverClient driverClient;
    private final RealtimePublisher publisher;

    @MessageMapping("/driver/location")
    public void location(@Payload Map<String, Object> telemetry, Principal principal) {
        UUID driverId = UUID.fromString(principal.getName());
        try {
            driverClient.ingest(driverId, telemetry);
        } catch (BusinessException e) {
            // Bao lai cho app tai xe de hien canh bao GPS yeu / dang Offline (UI-DRV-008)
            publisher.toUser(driverId, RealtimePublisher.QUEUE_NOTIFICATIONS, "TELEMETRY_REJECTED",
                    Map.of("code", e.getCode().name(), "message", e.getMessage()));
        }
    }
}
