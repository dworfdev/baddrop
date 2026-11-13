package com.dworf.slots_demo.unit;

import com.dworf.slots_demo.security.UsernamePolicy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class UsernamePolicyTest {

    @Test
    void normalizesCaseAndWhitespace() {
        assertEquals("alice_1", UsernamePolicy.validate("  Alice_1 "));
    }

    @Test
    void normalizesUnicodeCompatibilityForms() {
        // fullwidth "ＡＢＣ" must collapse to "abc", otherwise "abc" and "ＡＢＣ" are two different accounts
        assertEquals("abc", UsernamePolicy.validate("ＡＢＣ"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "ab", "aaaaaaaaaaaaaaaaaaaaaaaaa", "bad name", "bad-name", "<script>", "a\"b'c",
            "name<img src=x onerror=alert(1)>", "../etc", "ünï", "a;b", "user\n1"})
    void rejectsInvalidUsernames(String bad) {
        assertThrows(IllegalArgumentException.class, () -> UsernamePolicy.validate(bad));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> UsernamePolicy.validate(null));
    }
}
