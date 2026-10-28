package org.example.ride_hailing_logistics.pricing.dto.response;

import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;

import java.math.BigDecimal;
import java.util.UUID;

/** Snapshot ban giao cho trip-service khi tao chuyen (BR-007, DR-004). */
public record QuoteSnapshotResponse(UUID quoteId, UUID customerId, ServiceType serviceType, VehicleType vehicleType,
                                    double pickupLat, double pickupLng, String pickupAddress, double dropoffLat,
                                    double dropoffLng, String dropoffAddress, int distanceMeters, int durationSeconds,
                                    BigDecimal baseFare, BigDecimal distanceFare, BigDecimal timeFare,
                                    BigDecimal bookingFee, BigDecimal surgeMultiplier, BigDecimal total, String currency,
                                    UUID ruleId, int ruleVersion) {}
