package org.example.ride_hailing_logistics.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Bao tai xe thu nhap chuyen (trip-service day qua WebSocket). */
public record DriverEarningPosted(UUID tripId, UUID walletId, UUID driverId, BigDecimal netEarning, BigDecimal gross,
                                  BigDecimal commission, int commissionRuleVersion, BigDecimal balance,
                                  String currency, Instant postedAt) {}
