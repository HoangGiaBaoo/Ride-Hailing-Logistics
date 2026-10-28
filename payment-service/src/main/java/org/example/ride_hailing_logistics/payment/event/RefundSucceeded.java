package org.example.ride_hailing_logistics.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundSucceeded(UUID refundId, UUID paymentId, UUID tripId, UUID customerId, BigDecimal amount,
                              String currency, Instant refundedAt) {}
