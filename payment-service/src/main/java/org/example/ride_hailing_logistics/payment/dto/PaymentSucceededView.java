package org.example.ride_hailing_logistics.payment.dto;

import org.example.ride_hailing_logistics.common.domain.ServiceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Goc nhin cua vi ve event PaymentSucceeded: chi cac truong can dung, bo qua truong la (NFR-COMP-002). */
public record PaymentSucceededView(UUID paymentId, UUID tripId, String tripCode, String kind, UUID driverId,
                                   ServiceType serviceType, BigDecimal amount, String currency, String method,
                                   Instant succeededAt) {}
