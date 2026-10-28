package org.example.ride_hailing_logistics.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cong thuc cuoc (TBD-03), thuan va xac dinh: cung dau vao + cung quy tac -> cung ket qua (FR-PRI-011).
 *
 *   tam tinh = gia mo cua + gia/km x km + gia/phut x phut
 *   cuoc     = max(tam tinh x surge, cuoc toi thieu) + phi dat chuyen
 *   tong     = lam tron LEN boi so roundingStep (1.000 VND)
 *
 * Moi phep tinh deu tren BigDecimal; khoang cach/thoi gian doi sang km/phut voi 3 chu so le roi moi nhan.
 */
public final class FareCalculator {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    private FareCalculator() {
    }

    public static FareBreakdown calculate(RuleSnapshot rule, long distanceMeters, long durationSeconds,
                                          BigDecimal surge, BigDecimal roundingStep) {
        if (distanceMeters < 0 || durationSeconds < 0) {
            throw new IllegalArgumentException("Distance and duration must be non-negative");
        }
        if (surge.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Surge multiplier must be >= 1");
        }
        BigDecimal km = BigDecimal.valueOf(distanceMeters).divide(THOUSAND, 3, RoundingMode.HALF_UP);
        BigDecimal minutes = BigDecimal.valueOf(durationSeconds).divide(SIXTY, 3, RoundingMode.HALF_UP);

        BigDecimal base = rule.baseFare().setScale(0, RoundingMode.HALF_UP);
        BigDecimal distanceFare = rule.perKm().multiply(km).setScale(0, RoundingMode.HALF_UP);
        BigDecimal timeFare = rule.perMinute().multiply(minutes).setScale(0, RoundingMode.HALF_UP);
        BigDecimal raw = base.add(distanceFare).add(timeFare);

        BigDecimal surged = raw.multiply(surge).setScale(0, RoundingMode.HALF_UP);
        boolean minimumApplied = surged.compareTo(rule.minimumFare()) < 0;
        BigDecimal fare = (minimumApplied ? rule.minimumFare() : surged).add(rule.bookingFee());
        BigDecimal total = roundUp(fare, roundingStep);
        return new FareBreakdown(base, distanceFare, timeFare, rule.bookingFee().setScale(0, RoundingMode.HALF_UP),
                rule.minimumFare().setScale(0, RoundingMode.HALF_UP), surge, surged.subtract(raw), minimumApplied, total);
    }

    public static BigDecimal roundUp(BigDecimal amount, BigDecimal step) {
        return amount.divide(step, 0, RoundingMode.CEILING).multiply(step);
    }

    public record RuleSnapshot(BigDecimal baseFare, BigDecimal perKm, BigDecimal perMinute, BigDecimal minimumFare,
                               BigDecimal bookingFee) {}

    /** surgeAmount: phan tien tang them do surge, hien rieng cho khach thay (FR-PRI-010). */
    public record FareBreakdown(BigDecimal baseFare, BigDecimal distanceFare, BigDecimal timeFare, BigDecimal bookingFee,
                                BigDecimal minimumFare, BigDecimal surgeMultiplier, BigDecimal surgeAmount,
                                boolean minimumApplied, BigDecimal total) {}
}
