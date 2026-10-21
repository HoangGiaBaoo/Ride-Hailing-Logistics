package org.example.ride_hailing_logistics.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * He so surge (TBD-04, quy tac "surge-v1"):
 *   surge = clamp(1 + sensitivity x (cau / max(cung, 1) - 1), min, max), lam tron 1 chu so thap phan.
 * Cau <= cung thi surge = 1 (khong bao gio giam gia duoi muc thuong, khong am - FR-PRI-009).
 */
public final class SurgeCalculator {

    private SurgeCalculator() {
    }

    public static BigDecimal multiplier(long demand, long supply, BigDecimal sensitivity, BigDecimal min, BigDecimal max) {
        if (demand < 0 || supply < 0) {
            throw new IllegalArgumentException("Demand and supply must be non-negative");
        }
        BigDecimal ratio = BigDecimal.valueOf(demand).divide(BigDecimal.valueOf(Math.max(supply, 1)), 4, RoundingMode.HALF_UP);
        BigDecimal raw = BigDecimal.ONE.add(sensitivity.multiply(ratio.subtract(BigDecimal.ONE)));
        BigDecimal clamped = raw.max(min).min(max).max(BigDecimal.ONE);
        return clamped.setScale(1, RoundingMode.HALF_UP);
    }
}
