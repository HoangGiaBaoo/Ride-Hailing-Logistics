package org.example.ride_hailing_logistics.pricing.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Luu du thanh phan, quy tac, chinh sach de tai lap cuoc cuoi (FR-PRI-017). trip-service va payment-service nghe. */
public record FareFinalized(UUID tripId, String tripCode, UUID customerId, UUID driverId, String serviceType,
                            String paymentMethod, BigDecimal baseFare, BigDecimal distanceFare, BigDecimal timeFare,
                            BigDecimal bookingFee, BigDecimal surgeMultiplier, BigDecimal total, String currency,
                            UUID ruleId, int ruleVersion, String finalFarePolicy, Instant finalizedAt) {}
