package org.example.ride_hailing_logistics.pricing.dto;

import org.example.ride_hailing_logistics.pricing.domain.FareCalculator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Quy tac gia dang bat, dang luu trong cache Redis (doc nhieu, doi it). */
public record ActiveRule(UUID id, int ruleVersion, String currency, BigDecimal baseFare, BigDecimal perKm,
                         BigDecimal perMinute, BigDecimal minimumFare, BigDecimal bookingFee, Instant effectiveFrom,
                         Instant effectiveTo) {

    public boolean isEffectiveAt(Instant at) {
        return !effectiveFrom.isAfter(at) && (effectiveTo == null || effectiveTo.isAfter(at));
    }

    public FareCalculator.RuleSnapshot snapshot() {
        return new FareCalculator.RuleSnapshot(baseFare, perKm, perMinute, minimumFare, bookingFee);
    }
}
