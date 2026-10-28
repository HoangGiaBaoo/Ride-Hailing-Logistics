package org.example.ride_hailing_logistics.pricing.dto.response;

import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.pricing.dto.Place;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** FR-PRI-013: quote ID, dich vu, diem chuan hoa, khoang cach, ETA, thanh phan, surge, tong, tien te, rule version, han. */
public record QuoteResponse(UUID quoteId, ServiceType serviceType, VehicleType vehicleType, Place pickup, Place dropoff,
                            long distanceMeters, long durationSeconds, boolean approximateRoute,
                            FareBreakdownResponse breakdown, BigDecimal surgeMultiplier, boolean surgeConfirmationRequired,
                            BigDecimal total, String currency, int ruleVersion, String surgeVersion, Instant expiresAt) {}
