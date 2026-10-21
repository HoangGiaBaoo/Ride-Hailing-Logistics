package org.example.ride_hailing_logistics.trip.client;

import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;

import java.math.BigDecimal;
import java.util.UUID;

/** Bao gia da chot do pricing-service tra ve khi dung quote; chuyen sao chep lai de khong phu thuoc pricing ve sau. */
public record QuoteSnapshot(UUID quoteId, UUID customerId, ServiceType serviceType, VehicleType vehicleType,
                            double pickupLat, double pickupLng, String pickupAddress, double dropoffLat,
                            double dropoffLng, String dropoffAddress, int distanceMeters, int durationSeconds,
                            BigDecimal baseFare, BigDecimal distanceFare, BigDecimal timeFare, BigDecimal bookingFee,
                            BigDecimal surgeMultiplier, BigDecimal total, String currency, UUID ruleId,
                            int ruleVersion) {
}
