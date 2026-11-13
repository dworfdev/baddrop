package com.dworf.slots_demo.unit;

import com.dworf.slots_demo.security.PasswordPolicy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class PasswordPolicyTest {

    @Test
    void acceptsReasonablePassword() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("correct-horse-battery", "alice"));
    }

    @Test
    void rejectsBlankNullAndShort() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate(null, "alice"));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("          ", "alice"));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("short1", "alice"));
    }

    @Test
    void rejectsPasswordsLongerThanBcryptLimit() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("a1".repeat(40), "alice"));
        // multi-byte characters count in bytes, not chars: 40 x 2-byte chars = 80 bytes
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("é".repeat(40), "alice"));
    }

    @Test
    void rejectsCommonAndRepetitivePasswords() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("admin1234", "alice"));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("qwertyuiop", "alice"));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("aaaaaaaaaaaa", "alice"));
    }

    @Test
    void rejectsPasswordContainingUsername() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("my-alice-secret", "alice"));
    }
}
