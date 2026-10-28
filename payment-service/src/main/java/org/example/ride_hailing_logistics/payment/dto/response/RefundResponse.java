package org.example.ride_hailing_logistics.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(UUID id, BigDecimal amount, String currency, String reason, String status, Instant createdAt) {}
