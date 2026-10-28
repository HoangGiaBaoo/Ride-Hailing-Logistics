package org.example.ride_hailing_logistics.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.ride_hailing_logistics.payment.enums.EntryStatus;
import org.example.ride_hailing_logistics.payment.enums.EntryType;
import org.example.ride_hailing_logistics.payment.enums.ReferenceType;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** But toan bat bien (FR-WAL-002, FR-PAY-012). Sua sai bang but toan bu, khong bao gio UPDATE/DELETE. */
@Entity
@Immutable
@Table(name = "wallet_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletEntry {

    @Id
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private EntryType entryType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30)
    private ReferenceType referenceType;

    @Column(name = "reference_id", nullable = false, length = 64)
    private String referenceId;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntryStatus status;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static WalletEntry post(UUID walletId, EntryType type, BigDecimal signedAmount, String currency,
                            ReferenceType referenceType, String referenceId, String description, UUID createdBy,
                            Instant now) {
        WalletEntry e = new WalletEntry();
        e.id = UUID.randomUUID();
        e.walletId = walletId;
        e.entryType = type;
        e.amount = signedAmount;
        e.currency = currency;
        e.referenceType = referenceType;
        e.referenceId = referenceId;
        e.description = description;
        e.status = EntryStatus.POSTED;
        e.createdBy = createdBy;
        e.createdAt = now;
        return e;
    }
}
