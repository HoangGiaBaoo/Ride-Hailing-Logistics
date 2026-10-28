package org.example.ride_hailing_logistics.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentFailed(UUID paymentId, UUID tripId, UUID customerId, BigDecimal amount, String currency,
                            String reason, int attempts, Instant failedAt) {}
