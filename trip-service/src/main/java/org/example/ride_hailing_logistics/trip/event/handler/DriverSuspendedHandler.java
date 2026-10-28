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

/** DriverSuspended (driver-service) -> chan Online; dang ranh thi Offline ngay. */
@Component
@RequiredArgsConstructor
public class DriverSuspendedHandler implements EventHandler {

    private final DriverStateService driverStates;
    private final OutboxService outbox;

    @Override
    public String name() {
        return "trip.driver-suspended";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("DriverSuspended");
    }

    @Override
    public void handle(EventEnvelope event) {
        SuspendedPayload p = outbox.read(event, SuspendedPayload.class);
        driverStates.applySuspension(p.driverId(), true);
    }

    /** Hop dong voi driver-service; chi doc truong can dung. */
    public record SuspendedPayload(UUID driverId, String reason, Instant suspendedAt) {}
}
