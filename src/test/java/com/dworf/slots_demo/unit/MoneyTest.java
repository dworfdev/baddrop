package com.dworf.slots_demo.unit;

import com.dworf.slots_demo.security.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class MoneyTest {

    @Test
    void parsesPlainDecimalStrings() {
        assertEquals("12.50", Money.parse("12.5").toPlainString());
        assertEquals("1.00", Money.parse("1").toPlainString());
        assertEquals("0.01", Money.parse("0.01").toPlainString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "0.00", "-1", "1e3", "1.234", "1,5", " 1", "1 ", "NaN", "Infinity", "1000000000", "+5", "0x10"})
    void rejectsAmbiguousOrOutOfRangeStrings(String bad) {
        assertThrows(IllegalArgumentException.class, () -> Money.parse(bad));
    }

    @Test
    void requirePositiveNormalisesScale() {
        assertEquals("5.00", Money.requirePositive(new BigDecimal("5")).toPlainString());
        assertEquals("5.10", Money.requirePositive(new BigDecimal("5.1000")).toPlainString());
    }

    @Test
    void requirePositiveRejectsSubCentAndHugeValues() {
        assertThrows(IllegalArgumentException.class, () -> Money.requirePositive(new BigDecimal("0.001")));
        assertThrows(IllegalArgumentException.class, () -> Money.requirePositive(new BigDecimal("1.005")));
        assertThrows(IllegalArgumentException.class, () -> Money.requirePositive(new BigDecimal("100000.01")));
        assertThrows(IllegalArgumentException.class, () -> Money.requirePositive(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Money.requirePositive(new BigDecimal("-0.01")));
        assertThrows(IllegalArgumentException.class, () -> Money.requirePositive(null));
    }

    @Test
    void maximumIsAccepted() {
        assertEquals("100000.00", Money.requirePositive(new BigDecimal("100000")).toPlainString());
    }
}
