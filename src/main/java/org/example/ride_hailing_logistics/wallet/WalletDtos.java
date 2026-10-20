package org.example.ride_hailing_logistics.wallet;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.wallet.WalletEnums.EntryType;
import org.example.ride_hailing_logistics.wallet.WalletEnums.ReferenceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class WalletDtos {

    private WalletDtos() {
    }

    public record WalletResponse(UUID walletId, UUID driverId, String currency, BigDecimal balance, String status) {}

    public record EntryResponse(UUID id, EntryType type, BigDecimal amount, String currency, ReferenceType referenceType,
                                String referenceId, String description, Instant createdAt) {
        static EntryResponse from(WalletEntry e) {
            return new EntryResponse(e.getId(), e.getEntryType(), e.getAmount(), e.getCurrency(), e.getReferenceType(),
                    e.getReferenceId(), e.getDescription(), e.getCreatedAt());
        }
    }

    public record AdjustmentRequest(
            @NotNull @Digits(integer = 13, fraction = 0) BigDecimal amount,
            @NotBlank @Size(max = 255) String reason,
            @NotBlank @Size(max = 64) String reference) {}

    public record CommissionRuleRequest(
            @NotNull ServiceType serviceType,
            @NotNull @DecimalMin("0.0") @DecimalMax(value = "0.9999") @Digits(integer = 1, fraction = 4) BigDecimal rate,
            @NotNull Instant effectiveFrom) {}

    public record CommissionRuleResponse(UUID id, ServiceType serviceType, BigDecimal rate, int version,
                                         Instant effectiveFrom, Instant effectiveTo) {
        static CommissionRuleResponse from(CommissionRule r) {
            return new CommissionRuleResponse(r.getId(), r.getServiceType(), r.getRate(), r.getRuleVersion(),
                    r.getEffectiveFrom(), r.getEffectiveTo());
        }
    }
}
