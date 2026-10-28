package org.example.ride_hailing_logistics.pricing.dto.response;

import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PricingRuleResponse(UUID id, ServiceType serviceType, VehicleType vehicleType, String regionCode,
                                  BigDecimal baseFare, BigDecimal perKm, BigDecimal perMinute, BigDecimal minimumFare,
                                  BigDecimal bookingFee, String currency, int version, Instant effectiveFrom,
                                  Instant effectiveTo, boolean active) {}
