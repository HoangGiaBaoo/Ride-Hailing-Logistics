package org.example.ride_hailing_logistics.pricing.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import java.math.BigDecimal;
import java.time.Instant;

public record PricingRuleRequest(
        @NotNull ServiceType serviceType, @NotNull VehicleType vehicleType, @NotBlank @Size(max = 20) String regionCode,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 0) BigDecimal baseFare,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 0) BigDecimal perKm,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 0) BigDecimal perMinute,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 0) BigDecimal minimumFare,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 0) BigDecimal bookingFee,
        @NotNull Instant effectiveFrom, Instant effectiveTo,
        @NotBlank @Size(max = 500) String reason) {}
