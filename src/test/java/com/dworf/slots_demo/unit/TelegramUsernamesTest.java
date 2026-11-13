package com.dworf.slots_demo.unit;

import com.dworf.slots_demo.security.TelegramUsernames;
import com.dworf.slots_demo.security.UsernamePolicy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class TelegramUsernamesTest {

    @Test
    void prefersTheTelegramUsername() {
        assertEquals("ann_x", TelegramUsernames.derive("Ann_X", "Anna", 42));
    }

    @Test
    void fallsBackToFirstNameThenToId() {
        assertEquals("anna", TelegramUsernames.derive(null, "Anna", 42));
        assertEquals("tg42", TelegramUsernames.derive(null, "Иван", 42)); // Cyrillic: nothing usable left
        assertEquals("tg42", TelegramUsernames.derive("ab", null, 42));                       // too short
        assertEquals("tg9007199254740993", TelegramUsernames.derive(null, null, 9007199254740993L).substring(0, 18));
    }

    @Test
    void truncatesLongNamesAndAlwaysPassesTheUsernamePolicy() {
        String long32 = "abcdefghijklmnopqrstuvwxyz012345";
        String derived = TelegramUsernames.derive(long32, null, 1);
        assertEquals(24, derived.length());
        assertEquals(derived, UsernamePolicy.validate(derived));
        for (String junk : new String[]{"<script>alert(1)</script>", "a b c", "..//", "\n\n\n", "‮‮"}) {
            String d = TelegramUsernames.derive(junk, junk, 5);
            assertEquals(d, UsernamePolicy.validate(d), junk);
        }
    }

    @Test
    void suffixKeepsNamesUniqueAndValid() {
        String base = TelegramUsernames.derive("abcdefghijklmnopqrstuvwxyz", null, 1);
        String a = TelegramUsernames.withSuffix(base, 1234567, 0);
        String b = TelegramUsernames.withSuffix(base, 7654321, 0);
        assertNotEquals(a, b);
        assertTrue(a.length() <= 24 && b.length() <= 24);
        assertEquals(a, UsernamePolicy.validate(a));
        String retry = TelegramUsernames.withSuffix(base, 1234567, 3);
        assertNotEquals(a, retry);
        assertTrue(retry.length() <= 24);
    }
}
