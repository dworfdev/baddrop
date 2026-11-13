package com.dworf.slots_demo.unit;

import com.dworf.slots_demo.security.Totp;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class TotpTest {
    /** ASCII "12345678901234567890" in base32 - the RFC 6238 test key (SHA-1). */
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matchesRfc6238Vectors() {
        // RFC 6238 Appendix B gives 8-digit codes 94287082 / 07081804; the 6-digit codes are their last 6 digits.
        assertEquals("287082", Totp.code(RFC_SECRET, 59L));
        assertEquals("081804", Totp.code(RFC_SECRET, 1111111109L));
        assertEquals("050471", Totp.code(RFC_SECRET, 1111111111L));
    }

    @Test
    void acceptsCurrentCodeAndOneStepOfDrift() {
        long now = 1_700_000_000L;
        String code = Totp.code(RFC_SECRET, now);
        assertTrue(Totp.verify(RFC_SECRET, code, now, 1));
        assertTrue(Totp.verify(RFC_SECRET, code, now + 30, 1));
        assertTrue(Totp.verify(RFC_SECRET, code, now - 30, 1));
    }

    @Test
    void rejectsCodesFarOutsideTheWindow() {
        long now = 1_700_000_000L;
        String code = Totp.code(RFC_SECRET, now);
        assertFalse(Totp.verify(RFC_SECRET, code, now + 300, 1));
    }

    @Test
    void rejectsMalformedInput() {
        assertFalse(Totp.verify(RFC_SECRET, null, 59L, 1));
        assertFalse(Totp.verify(RFC_SECRET, "", 59L, 1));
        assertFalse(Totp.verify(RFC_SECRET, "28708", 59L, 1));
        assertFalse(Totp.verify(RFC_SECRET, "2870821", 59L, 1));
        assertFalse(Totp.verify(RFC_SECRET, "abcdef", 59L, 1));
    }

    @Test
    void generatedSecretsAreValidBase32AndDiffer() {
        String a = Totp.generateSecret();
        String b = Totp.generateSecret();
        assertNotEquals(a, b);
        assertTrue(a.matches("[A-Z2-7]{32}"));
        assertEquals(6, Totp.code(a, 1_700_000_000L).length());
    }
}
