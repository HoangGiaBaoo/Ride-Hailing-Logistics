package org.example.ride_hailing_logistics.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Payload event cua mien payment (SRS §5.6). */
public final class PaymentEvents {

    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String REFUND_SUCCEEDED = "RefundSucceeded";

    private PaymentEvents() {
    }

    public record PaymentSucceeded(UUID paymentId, UUID tripId, String tripCode, String kind, UUID customerId,
                                   UUID driverId, String serviceType, BigDecimal amount, String currency, String method,
                                   Instant succeededAt) {}

    public record PaymentFailed(UUID paymentId, UUID tripId, UUID customerId, BigDecimal amount, String currency,
                                String reason, int attempts, Instant failedAt) {}

    public record RefundSucceeded(UUID refundId, UUID paymentId, UUID tripId, UUID customerId, BigDecimal amount,
                                  String currency, Instant refundedAt) {}
}
