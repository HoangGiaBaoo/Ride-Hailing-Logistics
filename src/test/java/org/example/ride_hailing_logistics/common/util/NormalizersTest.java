package org.example.ride_hailing_logistics.common.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DR-VAL-003: chuan hoa truoc khi kiem duy nhat. */
class NormalizersTest {

    @ParameterizedTest
    @ValueSource(strings = {"0901234567", "+84 901 234 567", "84901234567", "090-123-4567", "(090) 123.4567"})
    void phoneVariantsNormalizeToSameNumber(String raw) {
        assertEquals(Optional.of("+84901234567"), Normalizers.phone(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0201234567", "12345", "090123456", "abc", ""})
    void invalidPhonesAreRejected(String raw) {
        assertTrue(Normalizers.phone(raw).isEmpty());
    }

    @Test
    void emailIsTrimmedAndLowercased() {
        assertEquals(Optional.of("an.nguyen@example.com"), Normalizers.email("  An.Nguyen@Example.COM "));
        assertTrue(Normalizers.email("not-an-email").isEmpty());
    }

    @Test
    void plateIsUppercasedWithoutSeparators() {
        assertEquals(Optional.of("51F12345"), Normalizers.plate("51f-123.45"));
        assertEquals(Optional.of("59X112345"), Normalizers.plate("59-X1 123.45"));
        assertTrue(Normalizers.plate("ABC").isEmpty());
    }

    @Test
    void phoneMaskingHidesMiddleDigits() {
        assertEquals("+84901***567", Normalizers.maskPhone("+84901234567"));
    }
}
