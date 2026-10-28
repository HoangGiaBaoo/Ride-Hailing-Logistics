package org.example.ride_hailing_logistics.trip.event.handler;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.example.ride_hailing_logistics.trip.service.DriverStateService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** UserStatusChanged (identity-service) -> tai khoan tai xe bi khoa thi khong duoc nhan chuyen. */
@Component
@RequiredArgsConstructor
public class UserStatusChangedHandler implements EventHandler {

    private final DriverStateService driverStates;
    private final OutboxService outbox;

    @Override
    public String name() {
        return "trip.user-status-changed";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("UserStatusChanged");
    }

    @Override
    public void handle(EventEnvelope event) {
        StatusPayload p = outbox.read(event, StatusPayload.class);
        driverStates.applyAccountStatus(p.userId(), !"ACTIVE".equals(p.status()));
    }

    /** Hop dong voi identity-service; chi doc truong can dung. */
    public record StatusPayload(UUID userId, String status, Instant changedAt) {}
}
