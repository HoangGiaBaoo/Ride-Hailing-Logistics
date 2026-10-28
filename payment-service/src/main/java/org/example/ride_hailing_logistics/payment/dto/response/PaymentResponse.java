package org.example.ride_hailing_logistics.payment.dto.response;

import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bien nhan cho khach (UI-CUS-007): khong lo providerRef day du. */
public record PaymentResponse(UUID id, UUID tripId, Payment.Kind kind, BigDecimal amount, BigDecimal refundedAmount,
                              String currency, PaymentMethod method, Payment.Status status, int attempts,
                              String failureReason, Instant succeededAt, Instant createdAt, List<RefundResponse> refunds) {}
