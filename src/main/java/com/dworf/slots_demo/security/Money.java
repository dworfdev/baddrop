package com.dworf.slots_demo.security;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** H16: strict amount parsing. No silent rounding, no scientific notation, bounded range. */
public final class Money {
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("100000.00");
    private Money() {}

    /** Accepts a plain decimal string with at most 2 fractional digits and a positive value. */
    public static BigDecimal parse(String raw) {
        if (raw == null || !raw.matches("\\d{1,9}(\\.\\d{1,2})?")) {
            throw new IllegalArgumentException("Amount must be a positive number with at most 2 decimals");
        }
        return requirePositive(new BigDecimal(raw).setScale(2, RoundingMode.UNNECESSARY));
    }

    /** Validates an already-parsed BigDecimal (e.g. from JSON): scale <= 2, 0 < x <= MAX. */
    public static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Amount must be provided");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than 0");
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amount must have at most 2 decimal places");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) throw new IllegalArgumentException("Amount exceeds the maximum");
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
