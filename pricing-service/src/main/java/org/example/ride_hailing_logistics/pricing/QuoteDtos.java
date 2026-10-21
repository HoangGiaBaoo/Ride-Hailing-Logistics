package org.example.ride_hailing_logistics.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class QuoteDtos {

    private QuoteDtos() {
    }

    /** Diem tren ban do: luu ca chuoi hien thi lan toa do chuan hoa (DR-VAL-002). */
    public record Place(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotBlank @Size(max = 255) String address) {}

    public record QuoteRequest(@NotNull ServiceType serviceType, @NotNull VehicleType vehicleType,
                               @NotNull @Valid Place pickup, @NotNull @Valid Place dropoff) {}

    public record FareBreakdownResponse(BigDecimal baseFare, BigDecimal distanceFare, BigDecimal timeFare,
                                        BigDecimal bookingFee, BigDecimal surgeAmount, BigDecimal minimumFare,
                                        boolean minimumApplied) {}

    /** FR-PRI-013: quote ID, dich vu, diem chuan hoa, khoang cach, ETA, thanh phan, surge, tong, tien te, rule version, han. */
    public record QuoteResponse(UUID quoteId, ServiceType serviceType, VehicleType vehicleType, Place pickup, Place dropoff,
                                long distanceMeters, long durationSeconds, boolean approximateRoute,
                                FareBreakdownResponse breakdown, BigDecimal surgeMultiplier, boolean surgeConfirmationRequired,
                                BigDecimal total, String currency, int ruleVersion, String surgeVersion, Instant expiresAt) {}

    /** Snapshot ban giao cho mien trip khi tao chuyen (BR-007, DR-004). */
    public record QuoteSnapshot(UUID quoteId, UUID customerId, ServiceType serviceType, VehicleType vehicleType,
                                double pickupLat, double pickupLng, String pickupAddress, double dropoffLat,
                                double dropoffLng, String dropoffAddress, int distanceMeters, int durationSeconds,
                                BigDecimal baseFare, BigDecimal distanceFare, BigDecimal timeFare, BigDecimal bookingFee,
                                BigDecimal surgeMultiplier, BigDecimal total, String currency, UUID ruleId,
                                int ruleVersion) {
        static QuoteSnapshot from(FareQuote q) {
            return new QuoteSnapshot(q.getId(), q.getCustomerId(), q.getServiceType(), q.getVehicleType(), q.getPickupLat(),
                    q.getPickupLng(), q.getPickupAddress(), q.getDropoffLat(), q.getDropoffLng(), q.getDropoffAddress(),
                    q.getDistanceMeters(), q.getDurationSeconds(), q.getBaseFare(), q.getDistanceFare(), q.getTimeFare(),
                    q.getBookingFee(), q.getSurgeMultiplier(), q.getTotal(), q.getCurrency(), q.getRuleId(), q.getRuleVersion());
        }
    }

    public record PricingRuleRequest(
            @NotNull ServiceType serviceType, @NotNull VehicleType vehicleType, @NotBlank @Size(max = 20) String regionCode,
            @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 0) BigDecimal baseFare,
            @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 0) BigDecimal perKm,
            @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 0) BigDecimal perMinute,
            @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 0) BigDecimal minimumFare,
            @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 0) BigDecimal bookingFee,
            @NotNull Instant effectiveFrom, Instant effectiveTo,
            @NotBlank @Size(max = 500) String reason) {}

    public record PricingRuleResponse(UUID id, ServiceType serviceType, VehicleType vehicleType, String regionCode,
                                      BigDecimal baseFare, BigDecimal perKm, BigDecimal perMinute, BigDecimal minimumFare,
                                      BigDecimal bookingFee, String currency, int version, Instant effectiveFrom,
                                      Instant effectiveTo, boolean active) {
        static PricingRuleResponse from(PricingRule r) {
            return new PricingRuleResponse(r.getId(), r.getServiceType(), r.getVehicleType(), r.getRegionCode(),
                    r.getBaseFare(), r.getPerKm(), r.getPerMinute(), r.getMinimumFare(), r.getBookingFee(), r.getCurrency(),
                    r.getRuleVersion(), r.getEffectiveFrom(), r.getEffectiveTo(), r.isActive());
        }
    }

    public record DeactivateRequest(@NotBlank @Size(max = 500) String reason) {}
}
