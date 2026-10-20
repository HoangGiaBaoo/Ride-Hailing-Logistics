package org.example.ride_hailing_logistics.pricing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** surge = clamp(1 + 0,25 x (cau/max(cung,1) - 1), 1,0, 2,5), lam tron 1 chu so (TBD-04). */
class SurgeCalculatorTest {

    private static final BigDecimal SENSITIVITY = new BigDecimal("0.25");
    private static final BigDecimal MIN = new BigDecimal("1.0");
    private static final BigDecimal MAX = new BigDecimal("2.5");

    @ParameterizedTest(name = "demand={0}, supply={1} -> {2}")
    @CsvSource({
            "0, 0, 1.0",      // 1 + 0,25 x (0 - 1) = 0,75 -> san 1,0 (khong giam gia, khong am)
            "0, 10, 1.0",
            "5, 10, 1.0",     // cau < cung
            "10, 10, 1.0",    // can bang: 1 + 0,25 x 0 = 1,0
            "3, 2, 1.1",      // 1 + 0,25 x 0,5 = 1,125 -> 1,1
            "7, 4, 1.2",      // 1 + 0,25 x 0,75 = 1,1875 -> 1,2
            "10, 2, 2.0",     // 1 + 0,25 x 4 = 2,0
            "2, 0, 1.3",      // cung 0 coi nhu 1: 1 + 0,25 x 1 = 1,25 -> 1,3
            "100, 1, 2.5"     // 25,75 -> tran 2,5
    })
    void multiplierFollowsFormula(long demand, long supply, String expected) {
        BigDecimal actual = SurgeCalculator.multiplier(demand, supply, SENSITIVITY, MIN, MAX);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "was " + actual);
        assertEquals(1, actual.scale());
    }
}
