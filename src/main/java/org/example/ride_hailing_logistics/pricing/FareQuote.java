package org.example.ride_hailing_logistics.pricing;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Bao gia luu phia server (FR-PRI-013): client chi giu quoteId, khong the sua gia (FR-PRI-015).
 * Luu du thanh phan + rule version de tai lap duoc ket qua (FR-PRI-011, FR-PRI-017).
 */
@Entity
@Table(name = "fare_quotes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FareQuote {

    public enum Status { ISSUED, USED }

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 20)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "region_code", nullable = false, length = 20)
    private String regionCode;

    @Column(name = "pickup_lat", nullable = false)
    private double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private double pickupLng;

    @Column(name = "pickup_address", nullable = false, length = 255)
    private String pickupAddress;

    @Column(name = "dropoff_lat", nullable = false)
    private double dropoffLat;

    @Column(name = "dropoff_lng", nullable = false)
    private double dropoffLng;

    @Column(name = "dropoff_address", nullable = false, length = 255)
    private String dropoffAddress;

    @Column(name = "distance_meters", nullable = false)
    private int distanceMeters;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(nullable = false)
    private boolean approximate;

    @Column(name = "base_fare", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "distance_fare", nullable = false, precision = 15, scale = 2)
    private BigDecimal distanceFare;

    @Column(name = "time_fare", nullable = false, precision = 15, scale = 2)
    private BigDecimal timeFare;

    @Column(name = "booking_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal bookingFee;

    @Column(name = "minimum_fare", nullable = false, precision = 15, scale = 2)
    private BigDecimal minimumFare;

    @Column(name = "surge_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal surgeMultiplier;

    @Column(name = "surge_version", nullable = false, length = 20)
    private String surgeVersion;

    @Column(name = "surge_cell", nullable = false, length = 12)
    private String surgeCell;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "rule_version", nullable = false)
    private int ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static FareQuote issue(UUID customerId, ServiceType serviceType, VehicleType vehicleType, String regionCode,
                           QuoteDtos.Place pickup, QuoteDtos.Place dropoff, RouteEstimator.RouteEstimate route,
                           FareCalculator.FareBreakdown fare, String surgeVersion, String surgeCell, String currency,
                           PricingRule rule, Instant expiresAt, Instant now) {
        FareQuote q = new FareQuote();
        q.id = UUID.randomUUID();
        q.customerId = customerId;
        q.serviceType = serviceType;
        q.vehicleType = vehicleType;
        q.regionCode = regionCode;
        q.pickupLat = pickup.latitude();
        q.pickupLng = pickup.longitude();
        q.pickupAddress = pickup.address().trim();
        q.dropoffLat = dropoff.latitude();
        q.dropoffLng = dropoff.longitude();
        q.dropoffAddress = dropoff.address().trim();
        q.distanceMeters = Math.toIntExact(route.distanceMeters());
        q.durationSeconds = Math.toIntExact(route.durationSeconds());
        q.approximate = route.approximate();
        q.baseFare = fare.baseFare();
        q.distanceFare = fare.distanceFare();
        q.timeFare = fare.timeFare();
        q.bookingFee = fare.bookingFee();
        q.minimumFare = fare.minimumFare();
        q.surgeMultiplier = fare.surgeMultiplier();
        q.surgeVersion = surgeVersion;
        q.surgeCell = surgeCell;
        q.total = fare.total();
        q.currency = currency;
        q.ruleId = rule.getId();
        q.ruleVersion = rule.getRuleVersion();
        q.status = Status.ISSUED;
        q.expiresAt = expiresAt;
        q.createdAt = now;
        return q;
    }
}
