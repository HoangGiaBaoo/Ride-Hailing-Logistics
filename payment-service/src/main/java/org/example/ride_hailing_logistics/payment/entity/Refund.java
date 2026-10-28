package org.example.ride_hailing_logistics.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Hoan tien: co ly do, nguoi yeu cau, lien ket giao dich goc (FR-PAY-011). */
@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "provider_ref", length = 64)
    private String providerRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Refund succeeded(UUID paymentId, BigDecimal amount, String currency, String reason, UUID requestedBy,
                            String providerRef, Instant now) {
        Refund r = new Refund();
        r.id = UUID.randomUUID();
        r.paymentId = paymentId;
        r.amount = amount;
        r.currency = currency;
        r.reason = reason;
        r.requestedBy = requestedBy;
        r.status = "SUCCEEDED";
        r.providerRef = providerRef;
        r.createdAt = now;
        return r;
    }
}
