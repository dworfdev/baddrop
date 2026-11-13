package com.dworf.slots_demo.unit;

import com.dworf.slots_demo.security.TelegramInitData;
import com.dworf.slots_demo.security.TelegramInitData.Verified;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class TelegramInitDataTest {

    private static final String TOKEN = "123456:TEST-TOKEN";
    private static final long NOW = 1_700_000_100L;
    private static final long MAX_AGE = 3600;

    /** Produced by an independent implementation (Python hmac/hashlib), not by TelegramInitData.sign. */
    private static final String PYTHON_VECTOR =
            "auth_date=1700000000&query_id=AAHtest&user=%7B%22id%22%3A42%2C%22first_name%22%3A%22Ann%22%2C%22username%22%3A%22ann_x%22%7D"
                    + "&hash=0cefc6c5b4bd5cd6a464b75101d55173ed6ca6267b019de54ed0d1e0eab620fa";

    @Test
    void acceptsDataSignedByAnIndependentImplementation() {
        Verified v = TelegramInitData.verify(PYTHON_VECTOR, TOKEN, NOW, MAX_AGE);
        assertEquals(42L, v.userId());
        assertEquals("ann_x", v.username());
        assertEquals("Ann", v.firstName());
        assertEquals(1_700_000_000L, v.authDate());
    }

    @Test
    void roundTripsOwnSignatureIncludingUnicodeNames() {
        String data = TelegramInitData.sign(TOKEN, 777L, null, "Иван \"Q\"", NOW);
        Verified v = TelegramInitData.verify(data, TOKEN, NOW, MAX_AGE);
        assertEquals(777L, v.userId());
        assertNull(v.username());
        assertEquals("Иван \"Q\"", v.firstName());
    }

    @Test
    void rejectsWrongBotToken() {
        assertThrows(IllegalArgumentException.class,
                () -> TelegramInitData.verify(PYTHON_VECTOR, "123456:OTHER", NOW, MAX_AGE));
    }

    @Test
    void rejectsTamperedUserId() {
        String forged = PYTHON_VECTOR.replace("%3A42%2C", "%3A43%2C");
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.verify(forged, TOKEN, NOW, MAX_AGE));
    }

    @Test
    void rejectsMissingOrBrokenHash() {
        String noHash = PYTHON_VECTOR.substring(0, PYTHON_VECTOR.indexOf("&hash="));
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.verify(noHash, TOKEN, NOW, MAX_AGE));
        assertThrows(IllegalArgumentException.class,
                () -> TelegramInitData.verify(noHash + "&hash=zz", TOKEN, NOW, MAX_AGE));
        assertThrows(IllegalArgumentException.class,
                () -> TelegramInitData.verify(noHash + "&hash=" + "0".repeat(64), TOKEN, NOW, MAX_AGE));
    }

    @Test
    void rejectsDuplicateFieldsAndGarbage() {
        assertThrows(IllegalArgumentException.class,
                () -> TelegramInitData.verify(PYTHON_VECTOR + "&auth_date=1700000099", TOKEN, NOW, MAX_AGE));
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.verify("not-a-query", TOKEN, NOW, MAX_AGE));
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.verify("", TOKEN, NOW, MAX_AGE));
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.verify(null, TOKEN, NOW, MAX_AGE));
        assertThrows(IllegalArgumentException.class,
                () -> TelegramInitData.verify("a=" + "x".repeat(5000), TOKEN, NOW, MAX_AGE));
    }

    @Test
    void rejectsStaleAndFutureDates() {
        String old = TelegramInitData.sign(TOKEN, 5L, "x_user", "X", NOW - MAX_AGE - 1);
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.verify(old, TOKEN, NOW, MAX_AGE));
        String edge = TelegramInitData.sign(TOKEN, 5L, "x_user", "X", NOW - MAX_AGE);
        assertEquals(5L, TelegramInitData.verify(edge, TOKEN, NOW, MAX_AGE).userId());
        String future = TelegramInitData.sign(TOKEN, 5L, "x_user", "X", NOW + 3600);
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.verify(future, TOKEN, NOW, MAX_AGE));
    }

    @Test
    void refusesToWorkWithoutABotToken() {
        assertThrows(IllegalStateException.class, () -> TelegramInitData.verify(PYTHON_VECTOR, "", NOW, MAX_AGE));
        assertThrows(IllegalStateException.class, () -> TelegramInitData.verify(PYTHON_VECTOR, null, NOW, MAX_AGE));
    }

    @Test
    void refusesBotsAndNonPositiveIds() {
        String bot = TelegramInitData.sign(TOKEN, "{\"id\":9,\"is_bot\":true}", NOW);
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.verify(bot, TOKEN, NOW, MAX_AGE));
        String zero = TelegramInitData.sign(TOKEN, "{\"id\":0}", NOW);
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.verify(zero, TOKEN, NOW, MAX_AGE));
        String noId = TelegramInitData.sign(TOKEN, "{\"first_name\":\"x\"}", NOW);
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.verify(noId, TOKEN, NOW, MAX_AGE));
    }

    @Test
    void parsesNestedObjectsEscapesAndUnicodeInTheUserJson() {
        String json = "{\"id\":7,\"first_name\":\"A\\\"B\\u0416\",\"extra\":{\"x\":[1,2,{\"y\":\"}\"}]},"
                + "\"username\":\"zed_9\",\"allows_write_to_pm\":true}";
        Verified v = TelegramInitData.verify(TelegramInitData.sign(TOKEN, json, NOW), TOKEN, NOW, MAX_AGE);
        assertEquals(7L, v.userId());
        assertEquals("A\"BЖ", v.firstName());
        assertEquals("zed_9", v.username());
    }

    @Test
    void rejectsMalformedUserJson() {
        String broken = TelegramInitData.sign(TOKEN, "{\"id\":7,", NOW);
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.verify(broken, TOKEN, NOW, MAX_AGE));
    }
}
