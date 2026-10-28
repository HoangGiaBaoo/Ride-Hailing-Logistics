package org.example.ride_hailing_logistics.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSucceeded(UUID paymentId, UUID tripId, String tripCode, String kind, UUID customerId,
                               UUID driverId, String serviceType, BigDecimal amount, String currency, String method,
                               Instant succeededAt) {}
