package org.example.ride_hailing_logistics.trip.client.dto;

import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;

import java.math.BigDecimal;
import java.util.UUID;

/** Hop dong voi pricing-service: snapshot bao gia chot vao chuyen (BR-007, DR-004). */
public record QuoteSnapshotResponse(UUID quoteId, UUID customerId, ServiceType serviceType, VehicleType vehicleType,
                                    double pickupLat, double pickupLng, String pickupAddress, double dropoffLat,
                                    double dropoffLng, String dropoffAddress, int distanceMeters, int durationSeconds,
                                    BigDecimal baseFare, BigDecimal distanceFare, BigDecimal timeFare,
                                    BigDecimal bookingFee, BigDecimal surgeMultiplier, BigDecimal total, String currency,
                                    UUID ruleId, int ruleVersion) {}
