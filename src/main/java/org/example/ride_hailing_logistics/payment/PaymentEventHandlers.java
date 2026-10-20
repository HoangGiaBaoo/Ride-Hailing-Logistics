package org.example.ride_hailing_logistics.payment;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/** Consumer cua payment: FareFinalized -> thu cuoc; TripCancelled co phi -> thu phi huy (SRS §5.6). */
@Configuration
@RequiredArgsConstructor
class PaymentEventHandlers {

    private final PaymentService paymentService;
    private final OutboxService outbox;

    @Bean
    EventHandler chargeFinalFare() {
        return new EventHandler() {
            @Override
            public String name() {
                return "payment.charge-fare";
            }

            @Override
            public Set<String> eventTypes() {
                return Set.of("FareFinalized");
            }

            @Override
            public void handle(EventEnvelope event) {
                FareView f = outbox.read(event, FareView.class);
                paymentService.createForTrip(f.tripId(), f.tripCode(), Payment.Kind.TRIP_FARE, f.customerId(), f.driverId(),
                        ServiceType.valueOf(f.serviceType()), f.total(), f.currency(), PaymentMethod.valueOf(f.paymentMethod()));
            }
        };
    }

    @Bean
    EventHandler chargeCancellationFee() {
        return new EventHandler() {
            @Override
            public String name() {
                return "payment.charge-cancellation-fee";
            }

            @Override
            public Set<String> eventTypes() {
                return Set.of("TripCancelled");
            }

            @Override
            public void handle(EventEnvelope event) {
                CancelView c = outbox.read(event, CancelView.class);
                if (c.feeAmount() == null || c.feeAmount().signum() <= 0) return;
                paymentService.createForTrip(c.tripId(), c.tripCode(), Payment.Kind.CANCELLATION_FEE, c.customerId(),
                        c.driverId(), ServiceType.valueOf(c.serviceType()), c.feeAmount(), c.currency(),
                        PaymentMethod.valueOf(c.paymentMethod()));
            }
        };
    }

    record FareView(UUID tripId, String tripCode, UUID customerId, UUID driverId, String serviceType, String paymentMethod,
                    BigDecimal total, String currency) {}

    record CancelView(UUID tripId, String tripCode, UUID customerId, UUID driverId, String serviceType, String paymentMethod,
                      BigDecimal feeAmount, String currency) {}
}
