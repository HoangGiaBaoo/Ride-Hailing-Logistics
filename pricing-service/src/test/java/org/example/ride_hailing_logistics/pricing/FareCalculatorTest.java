package org.example.ride_hailing_logistics.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cong thuc TBD-03 tinh tay; so sanh BigDecimal bang compareTo. */
class FareCalculatorTest {

    // Bang gia mau: mo cua 12.000, 5.000/km, 500/phut, toi thieu 20.000, phi dat chuyen 2.000
    private static final FareCalculator.RuleSnapshot RULE = new FareCalculator.RuleSnapshot(
            bd(12_000), bd(5_000), bd(500), bd(20_000), bd(2_000));
    private static final BigDecimal STEP = bd(1_000);

    @Test
    void fareFollowsHandCalculation() {
        // 4.200 m, 840 s (14 phut), surge 1.0
        //   12.000 + 4,2 x 5.000 + 14 x 500 = 12.000 + 21.000 + 7.000 = 40.000
        //   max(40.000, 20.000) + 2.000 = 42.000 (da la boi so 1.000)
        FareCalculator.FareBreakdown f = FareCalculator.calculate(RULE, 4_200, 840, bd("1.0"), STEP);
        assertMoney(12_000, f.baseFare());
        assertMoney(21_000, f.distanceFare());
        assertMoney(7_000, f.timeFare());
        assertMoney(0, f.surgeAmount());
        assertFalse(f.minimumApplied());
        assertMoney(42_000, f.total());
    }

    @Test
    void surgeMultipliesFareBeforeBookingFee() {
        // 40.000 x 1,5 = 60.000; + 2.000 = 62.000; phan tang do surge = 20.000 hien rieng (FR-PRI-010)
        FareCalculator.FareBreakdown f = FareCalculator.calculate(RULE, 4_200, 840, bd("1.5"), STEP);
        assertMoney(20_000, f.surgeAmount());
        assertMoney(62_000, f.total());
    }

    @Test
    void minimumFareAppliesToShortTrips() {
        // 500 m, 120 s: 12.000 + 2.500 + 1.000 = 15.500 < 20.000 -> 20.000 + 2.000 = 22.000
        FareCalculator.FareBreakdown f = FareCalculator.calculate(RULE, 500, 120, bd("1.0"), STEP);
        assertTrue(f.minimumApplied());
        assertMoney(22_000, f.total());
    }

    @Test
    void totalIsRoundedUpToNextThousand() {
        // 4.321 m, 600 s: 12.000 + 4,321 x 5.000 (= 21.605) + 10 x 500 (= 5.000) = 38.605
        //   + 2.000 = 40.605 -> lam tron LEN = 41.000
        FareCalculator.FareBreakdown f = FareCalculator.calculate(RULE, 4_321, 600, bd("1.0"), STEP);
        assertMoney(21_605, f.distanceFare());
        assertMoney(41_000, f.total());
    }

    @Test
    void sameInputAndRuleGiveSameResult() {
        // FR-PRI-011: tinh xac dinh
        assertEquals(FareCalculator.calculate(RULE, 7_777, 1_234, bd("1.3"), STEP),
                FareCalculator.calculate(RULE, 7_777, 1_234, bd("1.3"), STEP));
    }

    @Test
    void surgeBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FareCalculator.calculate(RULE, 1_000, 60, bd("0.9"), STEP));
    }

    private static void assertMoney(long expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
