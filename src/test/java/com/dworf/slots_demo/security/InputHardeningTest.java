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
    @ParameterizedTest
    @ValueSource(strings = {
            "<img src=x onerror=alert(1)>", "<script>alert(1)</script>", "\"><svg/onload=alert(1)>",
            "javascript:alert(1)", "a' OR '1'='1", "admin'--", "x; DROP TABLE users;--", "name with spaces", "emoji😀name", "a/b", "..", "%3Cscript%3E"})
    void hostileUsernamesCannotBeRegistered(String hostile) {
        ApiClient.Response r = anonymous().post("/api/auth/register", map("username", hostile, "password", PLAYER_PASSWORD));
        assertEquals(400, r.status(), "accepted hostile username: " + hostile + " -> " + r.body());
    }

    @Test
    void usernameLengthBoundsAreEnforced() {
        assertEquals(400, anonymous().post("/api/auth/register", map("username", "ab", "password", PLAYER_PASSWORD)).status());
        assertEquals(400, anonymous().post("/api/auth/register", map("username", "a".repeat(25), "password", PLAYER_PASSWORD)).status());
        assertEquals(201, anonymous().post("/api/auth/register", map("username", ("n" + System.nanoTime()).substring(0, 20), "password", PLAYER_PASSWORD)).status());
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
        ApiClient.Response r = anonymous().post("/api/auth/login", "{not json");
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
    void hugeBodiesAndPasswordsAreRejectedNotCrashed() {
        String huge = "a".repeat(200_000);
        ApiClient.Response r = anonymous().post("/api/auth/register", map("username", uniqueUsername(), "password", huge));
        assertEquals(400, r.status());
        assertCleanError(r);
    }

    @Test
    void wrongContentTypeIsA4xxNever5xx() {
        ApiClient.Response r = anonymous().send("POST", "/api/auth/login", "username=a&password=b",
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
        ApiClient.Response r = anonymous().post("/api/auth/login", map("username", "bob\u0000", "password", "x\u0007y"));
        assertTrue(r.status() == 400 || r.status() == 401, "status " + r.status());
    }

    private static void assertCleanError(ApiClient.Response r) {
        String b = r.body() == null ? "" : r.body();
        assertFalse(b.contains("Exception") || b.contains("\tat ") || b.contains("com.dworf") || b.contains("org.springframework")
                        || b.contains("org.hibernate") || b.toLowerCase().contains("sql") || b.contains("jackson"),
                "error body leaks internals: " + b);
    }
}
