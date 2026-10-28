package org.example.ride_hailing_logistics.payment.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Khong co truong so tien hoan "tuy y" ngoai so duong; tong luy ke duoc kiem o server (BR-010). */
public record RefundRequest(@NotNull @Positive @Digits(integer = 13, fraction = 0) BigDecimal amount,
                            @NotBlank @Size(max = 500) String reason) {}
