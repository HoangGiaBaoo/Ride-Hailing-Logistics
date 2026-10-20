package org.example.ride_hailing_logistics.pricing;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.QuoteSnapshot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Consumer cua mien pricing (SRS §5.6). Moi mien tu dinh nghia "goc nhin" payload, bo qua truong la (NFR-COMP-002). */
@Configuration
@RequiredArgsConstructor
class PricingEventHandlers {

    static final String FARE_FINALIZED = "FareFinalized";

    private final OutboxService outbox;
    private final SurgeService surgeService;
    private final QuoteService quoteService;
    private final Clock clock;

    /** TripRequested -> dem cau cho surge (FR-PRI-008: cau dua tren so yeu cau chuyen hop le). */
    @Bean
    EventHandler surgeDemandHandler() {
        return new EventHandler() {
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
        };
    }

    /**
     * TripCompleted -> chot cuoc cuoi (FR-PRI-017, FR-PRI-018).
     * Chinh sach gia tron goi (TBD-05): cuoc cuoi = tong da bao gia, giu dung he so surge khach da xac nhan.
     */
    @Bean
    EventHandler fareFinalizer() {
        return new EventHandler() {
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
                QuoteSnapshot q = quoteService.snapshot(trip.quoteId())
                        .orElseThrow(() -> new IllegalStateException("Quote " + trip.quoteId() + " not found"));
                outbox.append("pricing", "Trip", trip.tripId(), FARE_FINALIZED, 1, new FareFinalized(trip.tripId(),
                        trip.tripCode(), trip.customerId(), trip.driverId(), trip.serviceType(), trip.paymentMethod(),
                        q.baseFare(), q.distanceFare(), q.timeFare(), q.bookingFee(), q.surgeMultiplier(), q.total(),
                        q.currency(), q.ruleId(), q.ruleVersion(), "upfront-v1", clock.instant()));
            }
        };
    }

    record TripRequestedView(UUID tripId, double pickupLat, double pickupLng) {}

    record TripCompletedView(UUID tripId, String tripCode, UUID customerId, UUID driverId, UUID quoteId,
                             String serviceType, String paymentMethod) {}

    /** Luu du thanh phan, quy tac, chinh sach de tai lap cuoc cuoi (FR-PRI-017). */
    public record FareFinalized(UUID tripId, String tripCode, UUID customerId, UUID driverId, String serviceType,
                                String paymentMethod, BigDecimal baseFare, BigDecimal distanceFare, BigDecimal timeFare,
                                BigDecimal bookingFee, BigDecimal surgeMultiplier, BigDecimal total, String currency,
                                UUID ruleId, int ruleVersion, String finalFarePolicy, Instant finalizedAt) {}
}
