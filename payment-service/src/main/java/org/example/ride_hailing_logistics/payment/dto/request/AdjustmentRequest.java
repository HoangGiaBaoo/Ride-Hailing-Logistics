package org.example.ride_hailing_logistics.payment.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AdjustmentRequest(
        @NotNull @Digits(integer = 13, fraction = 0) BigDecimal amount,
        @NotBlank @Size(max = 255) String reason,
        @NotBlank @Size(max = 64) String reference) {}
