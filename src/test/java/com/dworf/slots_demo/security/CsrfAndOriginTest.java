package com.dworf.slots_demo.security;

import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("security")
@DisplayName("CSRF token and Origin enforcement on state-changing requests")
class CsrfAndOriginTest extends IntegrationTestBase {

    private static final Map<String, Object> DEPOSIT = map("amount", new BigDecimal("2.00"));

    @Test
    void stateChangingRequestWithoutCsrfTokenIsRejected() {
        ApiClient p = registerPlayer();
        ApiClient.Response r = p.send("POST", "/api/me/deposit/start", DEPOSIT, idem(), false);
        assertEquals(403, r.status(), r.body());
    }

    @Test
    void wrongCsrfTokenIsRejected() {
        ApiClient p = registerPlayer();
        Map<String, String> h = Map.of("X-CSRF-Token", "x".repeat(32), "Idempotency-Key", freshKey());
        assertEquals(403, p.send("POST", "/api/me/deposit/start", DEPOSIT, h, false).status());
    }

    @Test
    void anotherSessionsCsrfTokenIsRejected() {
        ApiClient a = registerPlayer();
        ApiClient b = registerPlayer();
        Map<String, String> h = Map.of("X-CSRF-Token", b.csrf(), "Idempotency-Key", freshKey());
        assertEquals(403, a.send("POST", "/api/me/deposit/start", DEPOSIT, h, false).status());
    }

    @Test
    void validCsrfTokenIsAccepted() {
        ApiClient p = registerPlayer();
        assertEquals(200, p.post("/api/me/deposit/start", DEPOSIT, idem()).status());
    }

    @Test
    void safeGetRequestsDoNotNeedACsrfToken() {
        ApiClient p = registerPlayer();
        assertEquals(200, p.send("GET", "/api/me", null, Map.of(), false).status());
    }

    @Test
    void foreignOriginIsRejectedEvenWithAValidToken() {
        ApiClient p = registerPlayer();
        Map<String, String> h = Map.of("Origin", "https://evil.example", "Idempotency-Key", freshKey());
        assertEquals(403, p.post("/api/me/deposit/start", DEPOSIT, h).status());
    }

    @Test
    void foreignOriginIsRejectedOnTelegramLoginToo() {
        Map<String, String> h = Map.of("Origin", "https://evil.example");
        assertEquals(403, anonymous().post("/api/auth/telegram", map("initData", initData(7_700_000_001L, uniqueUsername())), h).status(), "login CSRF");
    }

    @Test
    void sameOriginRequestsPass() {
        ApiClient p = registerPlayer();
        Map<String, String> h = Map.of("Origin", "http://localhost:" + port, "Idempotency-Key", freshKey());
        assertEquals(200, p.post("/api/me/deposit/start", DEPOSIT, h).status());
    }

    @Test
    void originWithDifferentPortOrSuffixIsRejected() {
        ApiClient p = registerPlayer();
        for (String origin : new String[]{"http://localhost:" + (port + 1), "http://localhost.evil.example:" + port, "null", "not a url"}) {
            Map<String, String> h = Map.of("Origin", origin, "Idempotency-Key", freshKey());
            assertEquals(403, p.post("/api/me/deposit/start", DEPOSIT, h).status(), "Origin: " + origin);
        }
    }

    @Test
    void corsPreflightIsNeverAnswered() {
        ApiClient.Response r = anonymous().send("OPTIONS", "/api/me/withdraw", null,
                Map.of("Origin", "https://evil.example", "Access-Control-Request-Method", "POST"), false);
        assertEquals(403, r.status());
        assertEquals(null, r.header("Access-Control-Allow-Origin"));
    }

    @Test
    void adminActionsNeedACsrfTokenToo() {
        ApiClient a = admin();
        assertEquals(403, a.send("POST", "/api/admin/transactions/" + "a".repeat(64) + "/reject", "{}", Map.of(), false).status());
        assertEquals(403, a.send("POST", "/api/admin/auth/logout", "{}", Map.of(), false).status());
    }

    @Test
    void logoutWithoutTokenOnlyTouchesTheCallersOwnCookie() {
        ApiClient p = registerPlayer();
        assertEquals(200, p.send("POST", "/api/auth/logout", "{}", Map.of(), false).status());
    }
}
