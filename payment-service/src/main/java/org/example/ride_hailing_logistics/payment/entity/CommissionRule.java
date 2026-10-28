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
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Cong thuc hoa hong co phien ban, loai dich vu va thoi gian hieu luc (FR-WAL-004). */
@Entity
@Table(name = "commission_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommissionRule {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 20)
    private ServiceType serviceType;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal rate;

    @Column(name = "rule_version", nullable = false)
    private int ruleVersion;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static CommissionRule create(ServiceType type, BigDecimal rate, int version, Instant from, Instant to,
                                        UUID createdBy, Instant now) {
        CommissionRule r = new CommissionRule();
        r.id = UUID.randomUUID();
        r.serviceType = type;
        r.rate = rate;
        r.ruleVersion = version;
        r.effectiveFrom = from;
        r.effectiveTo = to;
        r.createdBy = createdBy;
        r.createdAt = now;
        return r;
    }

    /** Quy tac moi co hieu luc thi quy tac cu duoc dong lai tai thoi diem do, khong sua noi dung cu. */
    public void closeAt(Instant at) {
        if (effectiveTo == null || effectiveTo.isAfter(at)) {
            effectiveTo = at;
        }
    }
}
