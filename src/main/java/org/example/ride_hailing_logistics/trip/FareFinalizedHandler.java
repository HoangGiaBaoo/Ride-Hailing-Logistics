package org.example.ride_hailing_logistics.trip;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/** FareFinalized -> luu cuoc cuoi vao chuyen de hien bien nhan (UI-CUS-007). */
@Component
@RequiredArgsConstructor
class FareFinalizedHandler implements EventHandler {

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
        FareView fare = outbox.read(event, FareView.class);
        tripService.recordFinalFare(fare.tripId(), fare.total());
    }

    record FareView(UUID tripId, BigDecimal total) {}
}
