package org.example.ride_hailing_logistics.location.event.handler;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.location.event.DriverAvailabilityChanged;
import org.example.ride_hailing_logistics.location.service.LocationService;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.util.Set;

/** DriverAvailabilityChanged (trip-service) -> cap nhat ngu canh; Offline thi xoa khoi chi muc ngay (FR-LOC-008). */
@Component
@RequiredArgsConstructor
public class DriverAvailabilityChangedHandler implements EventHandler {

    private final LocationService locationService;
    private final OutboxService outbox;

    @Override
    public String name() {
        return "location.availability-changed";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("DriverAvailabilityChanged");
    }

    @Override
    public void handle(EventEnvelope event) {
        locationService.applyAvailability(outbox.read(event, DriverAvailabilityChanged.class));
    }
}
