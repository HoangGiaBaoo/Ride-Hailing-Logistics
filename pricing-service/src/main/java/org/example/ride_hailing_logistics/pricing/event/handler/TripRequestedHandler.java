package org.example.ride_hailing_logistics.pricing.event.handler;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.pricing.service.SurgeService;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/** TripRequested -> dem cau cho surge (FR-PRI-008: cau dua tren so yeu cau chuyen hop le). */
@Component
@RequiredArgsConstructor
public class TripRequestedHandler implements EventHandler {

    private final SurgeService surgeService;
    private final OutboxService outbox;

    @Override
    public String name() {
        return "pricing.surge-demand";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("TripRequested");
    }

    @Override
    public void handle(EventEnvelope event) {
        TripRequestedView trip = outbox.read(event, TripRequestedView.class);
        surgeService.recordDemand(trip.pickupLat(), trip.pickupLng());
    }

    record TripRequestedView(UUID tripId, double pickupLat, double pickupLng) {}
}
