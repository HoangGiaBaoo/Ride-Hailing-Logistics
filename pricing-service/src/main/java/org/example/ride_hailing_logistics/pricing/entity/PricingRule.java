package org.example.ride_hailing_logistics.pricing.entity;

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
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.pricing.domain.FareCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Bang gia theo dich vu / loai xe / vung / khoang hieu luc (FR-PRI-001, FR-PRI-002).
 * Khong sua noi dung quy tac da tao: doi gia = tao quy tac moi phien ban moi, quote cu giu nguyen (FR-PRI-012).
 */
@Entity
@Table(name = "pricing_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PricingRule {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 20)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "region_code", nullable = false, length = 20)
    private String regionCode;

    @Column(name = "base_fare", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "per_km", nullable = false, precision = 15, scale = 2)
    private BigDecimal perKm;

    @Column(name = "per_minute", nullable = false, precision = 15, scale = 2)
    private BigDecimal perMinute;

    @Column(name = "minimum_fare", nullable = false, precision = 15, scale = 2)
    private BigDecimal minimumFare;

    @Column(name = "booking_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal bookingFee;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "rule_version", nullable = false)
    private int ruleVersion;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static PricingRule create(ServiceType serviceType, VehicleType vehicleType, String regionCode,
                                     BigDecimal baseFare, BigDecimal perKm, BigDecimal perMinute, BigDecimal minimumFare,
                                     BigDecimal bookingFee, String currency, int version, Instant from, Instant to,
                                     UUID createdBy, Instant now) {
        PricingRule r = new PricingRule();
        r.id = UUID.randomUUID();
        r.serviceType = serviceType;
        r.vehicleType = vehicleType;
        r.regionCode = regionCode;
        r.baseFare = baseFare;
        r.perKm = perKm;
        r.perMinute = perMinute;
        r.minimumFare = minimumFare;
        r.bookingFee = bookingFee;
        r.currency = currency;
        r.ruleVersion = version;
        r.effectiveFrom = from;
        r.effectiveTo = to;
        r.active = true;
        r.createdBy = createdBy;
        r.createdAt = now;
        return r;
    }

    public FareCalculator.RuleSnapshot snapshot() {
        return new FareCalculator.RuleSnapshot(baseFare, perKm, perMinute, minimumFare, bookingFee);
    }

    public void deactivate() {
        active = false;
    }
}
