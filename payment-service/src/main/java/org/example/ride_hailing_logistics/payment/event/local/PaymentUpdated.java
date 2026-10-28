package org.example.ride_hailing_logistics.payment.event.local;

import java.math.BigDecimal;
import java.util.UUID;

/** Event trong tien trinh cho realtime: bao khach trang thai thanh toan sau commit (NFR-USA-002). */
public record PaymentUpdated(UUID paymentId, UUID tripId, UUID customerId, String status, BigDecimal amount,
                             String currency) {}
