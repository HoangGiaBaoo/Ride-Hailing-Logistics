package org.example.ride_hailing_logistics.payment.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import java.math.BigDecimal;
import java.time.Instant;

public record CommissionRuleRequest(
        @NotNull ServiceType serviceType,
        @NotNull @DecimalMin("0.0") @DecimalMax(value = "0.9999") @Digits(integer = 1, fraction = 4) BigDecimal rate,
        @NotNull Instant effectiveFrom) {}
