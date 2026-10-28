package org.example.ride_hailing_logistics.payment.event.handler;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.payment.entity.Payment;
import org.example.ride_hailing_logistics.payment.service.PaymentService;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/** FareFinalized (pricing-service) -> tao khoan thu cuoc chuyen; so tien lay tu cuoc da chot, khong tu client (FR-PAY-002). */
@Component
@RequiredArgsConstructor
public class FareFinalizedHandler implements EventHandler {

    private final PaymentService paymentService;
    private final OutboxService outbox;

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
        FareFinalizedPayload f = outbox.read(event, FareFinalizedPayload.class);
        paymentService.createForTrip(f.tripId(), f.tripCode(), Payment.Kind.TRIP_FARE, f.customerId(), f.driverId(),
                ServiceType.valueOf(f.serviceType()), f.total(), f.currency(), PaymentMethod.valueOf(f.paymentMethod()));
    }

    public record FareFinalizedPayload(UUID tripId, String tripCode, UUID customerId, UUID driverId, String serviceType,
                                       String paymentMethod, BigDecimal total, String currency) {}
}
