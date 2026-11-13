package com.dworf.slots_demo.unit;

import com.dworf.slots_demo.security.TonAddressValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class TonAddressValidatorTest {
    /** The deposit address used by the application; its CRC16 was verified independently. */
    private static final String VALID = "UQCys13azAWTFuERWwjc_5sqKZxQEhJhbtGGaiyp2Sf55Hrg";

    @Test
    void acceptsValidNonBounceableMainnetAddress() {
        assertTrue(TonAddressValidator.isValid(VALID));
        assertTrue(TonAddressValidator.isMainnet(VALID));
    }

    @Test
    void acceptsSurroundingWhitespace() {
        assertTrue(TonAddressValidator.isValid("  " + VALID + " "));
    }

    @Test
    void rejectsWhenChecksumIsWrong() {
        String tampered = VALID.substring(0, 47) + (VALID.charAt(47) == 'A' ? 'B' : 'A');
        assertFalse(TonAddressValidator.isValid(tampered));
    }

    @Test
    void rejectsWhenAnyPayloadCharacterChanges() {
        String tampered = VALID.substring(0, 10) + (VALID.charAt(10) == 'a' ? 'b' : 'a') + VALID.substring(11);
        assertFalse(TonAddressValidator.isValid(tampered));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"hello", "0:abcdef", "UQ", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "UQCys13azAWTFuERWwjc_5sqKZxQEhJhbtGGaiyp2Sf55Hr!",
            "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq", "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"})
    void rejectsGarbageAndOtherChains(String bad) {
        assertFalse(TonAddressValidator.isValid(bad));
    }
}
