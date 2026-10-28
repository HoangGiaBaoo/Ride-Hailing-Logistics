package org.example.ride_hailing_logistics.pricing.event.handler;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.pricing.service.QuoteService;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * TripCompleted -> chot cuoc cuoi (FR-PRI-017, FR-PRI-018) va phat FareFinalized cung transaction (outbox).
 * Chinh sach gia tron goi (TBD-05): cuoc cuoi = tong da bao gia, giu dung he so surge khach da xac nhan.
 */
@Component
@RequiredArgsConstructor
public class TripCompletedHandler implements EventHandler {

    private final QuoteService quoteService;
    private final OutboxService outbox;

    @Override
    public String name() {
        return "pricing.finalize-fare";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("TripCompleted");
    }

    @Override
    public void handle(EventEnvelope event) {
        TripCompletedView trip = outbox.read(event, TripCompletedView.class);
        quoteService.finalizeFare(trip.tripId(), trip.tripCode(), trip.customerId(), trip.driverId(), trip.quoteId(),
                trip.serviceType(), trip.paymentMethod());
    }

    record TripCompletedView(UUID tripId, String tripCode, UUID customerId, UUID driverId, UUID quoteId,
                             String serviceType, String paymentMethod) {}
}
