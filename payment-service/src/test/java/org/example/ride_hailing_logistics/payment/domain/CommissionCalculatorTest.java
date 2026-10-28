package org.example.ride_hailing_logistics.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** BR-012: thu nhap rong = tong - hoa hong; hoa hong lam tron toi dong, HALF_UP. */
class CommissionCalculatorTest {

    @Test
    void twentyPercentOfRideFare() {
        // 87.000 x 20% = 17.400; rong = 69.600
        CommissionCalculator.Split s = CommissionCalculator.split(bd("87000"), bd("0.20"));
        assertMoney("17400", s.commission());
        assertMoney("69600", s.net());
        assertMoney("87000", s.commission().add(s.net()));   // khong mat dong nao
    }

    @Test
    void roundsHalfUpToWholeDong() {
        // 12.347 x 15% = 1.852,05 -> 1.852 ; 12.350 x 15% = 1.852,5 -> 1.853
        assertMoney("1852", CommissionCalculator.split(bd("12347"), bd("0.15")).commission());
        assertMoney("1853", CommissionCalculator.split(bd("12350"), bd("0.15")).commission());
    }

    @Test
    void zeroFareGivesZeroCommission() {
        assertMoney("0", CommissionCalculator.split(BigDecimal.ZERO, bd("0.20")).commission());
    }

    @Test
    void invalidRateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CommissionCalculator.split(bd("1000"), bd("1.0")));
        assertThrows(IllegalArgumentException.class, () -> CommissionCalculator.split(bd("1000"), bd("-0.1")));
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual), "was " + actual);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
