package com.dworf.slots_demo.security;

import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("security")
@DisplayName("Input hardening: XSS payloads, SQL metacharacters, malformed bodies, error hygiene")
class InputHardeningTest extends IntegrationTestBase {

    /** C2 / H21: stored XSS in the admin panel was possible because any string was a valid username. */
    /** C2 / H21: stored XSS in the admin panel was possible because any string was a valid username. */
    @ParameterizedTest
    @ValueSource(strings = {
            "<img src=x onerror=alert(1)>", "<script>alert(1)</script>", "\"><svg/onload=alert(1)>",
            "javascript:alert(1)", "a' OR '1'='1", "admin'--", "x; DROP TABLE users;--", "name with spaces", "emoji😀name", "a/b", "..", "%3Cscript%3E", "ab", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void hostileTelegramNamesAreSanitisedIntoAValidUsername(String hostile) {
        long id = 7_600_000_000L + Math.abs(hostile.hashCode() % 1_000_000);
        String signed = com.dworf.slots_demo.security.TelegramInitData.sign(
                BOT_TOKEN, id, hostile, hostile, java.time.Instant.now().getEpochSecond());
        ApiClient c = anonymous();
        ApiClient.Response r = c.post("/api/auth/telegram", map("initData", signed));
        assertEquals(200, r.status(), "login failed for " + hostile + " -> " + r.body());
        String username = (String) ((Map<String, Object>) r.json().get("user")).get("username");
        assertTrue(username.matches("[a-z0-9_]{3,24}"), "username escaped the policy: " + username);
    }

    @Test
    void sqlMetacharactersInAdminLookupDoNotBreakAnything() {
        ApiClient a = admin();
        for (String evil : new String[]{"'", "%27%20OR%201=1", "a%27--"}) {
            int s = a.get("/api/admin/users/" + evil).status();
            assertTrue(s == 400 || s == 404, evil + " -> " + s);
        }
    }

    @Test
    void malformedJsonYieldsACleanBadRequest() {
        ApiClient.Response r = anonymous().post("/api/auth/telegram", "{not json");
        assertEquals(400, r.status());
        assertCleanError(r);
    }

    @Test
    void wrongTypesYieldACleanBadRequest() {
        ApiClient p = registerPlayer();
        for (String body : new String[]{"{\"amount\":\"abc\"}", "{\"amount\":[1]}", "{\"amount\":{\"a\":1}}", "[]", "\"str\"", "null"}) {
            ApiClient.Response r = p.post("/api/me/deposit/start", body, idem());
            assertTrue(r.status() == 400, body + " -> " + r.status() + " " + r.body());
            assertCleanError(r);
        }
    }

    @Test
    void hugeInitDataIsRejectedNotCrashed() {
        String huge = "a".repeat(200_000);
        ApiClient.Response r = anonymous().post("/api/auth/telegram", map("initData", huge));
        assertTrue(r.status() >= 400 && r.status() < 500, "status " + r.status());
        assertCleanError(r);
    }

    @Test
    void wrongContentTypeIsA4xxNever5xx() {
        ApiClient.Response r = anonymous().send("POST", "/api/auth/telegram", "initData=a",
                Map.of("Content-Type", "application/x-www-form-urlencoded"), false);
        assertTrue(r.status() >= 400 && r.status() < 500, "status " + r.status());
        assertCleanError(r);
    }

    @Test
    void unknownRoutesAndMethodsDoNotLeakInternals() {
        ApiClient p = registerPlayer();
        ApiClient.Response notFound = p.get("/api/me/does-not-exist");
        assertEquals(404, notFound.status());
        assertCleanError(notFound);
        ApiClient.Response wrongMethod = p.send("DELETE", "/api/me/balance", null, Map.of(), true);
        assertTrue(wrongMethod.status() == 405 || wrongMethod.status() == 404, "status " + wrongMethod.status());
        assertCleanError(wrongMethod);
    }

    @Test
    void nullBytesAndControlCharactersAreHandled() {
        ApiClient.Response r = anonymous().post("/api/auth/telegram", map("initData", "user=bob\u0000&hash=x\u0007y"));
        assertTrue(r.status() == 400 || r.status() == 401, "status " + r.status());
    }

    private static void assertCleanError(ApiClient.Response r) {
        String b = r.body() == null ? "" : r.body();
        assertFalse(b.contains("Exception") || b.contains("\tat ") || b.contains("com.dworf") || b.contains("org.springframework")
                        || b.contains("org.hibernate") || b.toLowerCase().contains("sql") || b.contains("jackson"),
                "error body leaks internals: " + b);
    }
}
