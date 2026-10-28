package org.example.ride_hailing_logistics.payment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Tinh hoa hong va thu nhap rong (FR-WAL-003, BR-012). Thuan, khong Spring. */
public final class CommissionCalculator {

    private CommissionCalculator() {
    }

    /** VND khong co dong le: hoa hong lam tron toi dong, HALF_UP. */
    public static Split split(BigDecimal gross, BigDecimal rate) {
        if (gross.signum() < 0 || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Invalid gross or commission rate");
        }
        BigDecimal commission = gross.multiply(rate).setScale(0, RoundingMode.HALF_UP);
        return new Split(gross, commission, gross.subtract(commission));
    }

    public record Split(BigDecimal gross, BigDecimal commission, BigDecimal net) {}
}
