package org.example.ride_hailing_logistics.trip.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import java.util.UUID;

/** Khong co truong gia, trang thai hay customerId: client khong duoc tu quyet (NFR-SEC-012). */
public record CreateTripRequest(
        @NotNull UUID quoteId,
        @NotNull PaymentMethod paymentMethod,
        boolean surgeConfirmed,
        @Valid RecipientRequest recipient,
        @Size(max = 500) String packageDescription,
        @Positive @DecimalMax("500") Double packageWeightKg) {}
