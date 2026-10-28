package org.example.ride_hailing_logistics.trip.event.handler;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.example.ride_hailing_logistics.trip.service.TripService;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/** FareFinalized -> luu cuoc cuoi vao chuyen de hien bien nhan (UI-CUS-007). */
@Component
@RequiredArgsConstructor
public class FareFinalizedHandler implements EventHandler {

    private final TripService tripService;
    private final OutboxService outbox;

    @Override
    public String name() {
        return "trip.record-final-fare";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("FareFinalized");
    }

    @Override
    public void handle(EventEnvelope event) {
        FareFinalizedPayload fare = outbox.read(event, FareFinalizedPayload.class);
        tripService.recordFinalFare(fare.tripId(), fare.total());
    }

    public record FareFinalizedPayload(UUID tripId, BigDecimal total) {}
}
