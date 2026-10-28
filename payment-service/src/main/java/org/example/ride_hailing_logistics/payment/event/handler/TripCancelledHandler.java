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

/** TripCancelled co phi (trip-service) -> tao khoan thu phi huy (FR-CAN-004). Huy mien phi thi bo qua. */
@Component
@RequiredArgsConstructor
public class TripCancelledHandler implements EventHandler {

    private final PaymentService paymentService;
    private final OutboxService outbox;

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
        TripCancelledPayload c = outbox.read(event, TripCancelledPayload.class);
        if (c.feeAmount() == null || c.feeAmount().signum() <= 0) return;
        paymentService.createForTrip(c.tripId(), c.tripCode(), Payment.Kind.CANCELLATION_FEE, c.customerId(),
                c.driverId(), ServiceType.valueOf(c.serviceType()), c.feeAmount(), c.currency(),
                PaymentMethod.valueOf(c.paymentMethod()));
    }

    public record TripCancelledPayload(UUID tripId, String tripCode, UUID customerId, UUID driverId, String serviceType,
                                       String paymentMethod, BigDecimal feeAmount, String currency) {}
}
