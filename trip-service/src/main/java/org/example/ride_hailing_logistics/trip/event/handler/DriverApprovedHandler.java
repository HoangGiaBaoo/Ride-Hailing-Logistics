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

/** DriverApproved (driver-service, ca khi duyet lai sau dinh chi) -> bo co dinh chi. */
@Component
@RequiredArgsConstructor
public class DriverApprovedHandler implements EventHandler {

    private final DriverStateService driverStates;
    private final OutboxService outbox;

    @Override
    public String name() {
        return "trip.driver-approved";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("DriverApproved");
    }

    @Override
    public void handle(EventEnvelope event) {
        ApprovedPayload p = outbox.read(event, ApprovedPayload.class);
        driverStates.applySuspension(p.driverId(), false);
    }

    /** Hop dong voi driver-service; chi doc truong can dung. */
    public record ApprovedPayload(UUID driverId, Instant approvedAt) {}
}
