package com.dworf.slots_demo.security;

import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("security")
@DisplayName("Session cookie attributes and token quality")
class SessionCookieTest extends IntegrationTestBase {

    private static String setCookie(ApiClient.Response r) {
        return r.headerAll("Set-Cookie").stream().filter(c -> c.startsWith("BD_SESSION=")).findFirst().orElse(null);
    }

    @Test
    void loginCookieIsHttpOnlySecureSameSiteStrict() {
        String name = uniqueUsername();
        registerPlayer(name);
        ApiClient.Response r = anonymous().post("/api/auth/telegram", map("initData", initData(telegramIdOf(name), name)));
        String c = setCookie(r);
        assertNotNull(c, "login must set BD_SESSION");
        String lower = c.toLowerCase();
        assertTrue(lower.contains("httponly"), c);
        assertTrue(lower.contains("secure"), c);
        assertTrue(lower.contains("samesite=strict"), c);
        assertTrue(lower.contains("path=/"), c);
        assertTrue(lower.contains("max-age="), c);
    }

    @Test
    void adminCookieHasTheSameAttributes() {
        ApiClient c = anonymous();
        ApiClient.Response r = c.post("/api/admin/auth/login", map("username", ADMIN_USER, "password", ADMIN_PASSWORD,
                "totp", com.dworf.slots_demo.security.Totp.code(ADMIN_TOTP_SECRET, java.time.Instant.now().getEpochSecond())));
        assertEquals(200, r.status(), r.body());
        String lower = setCookie(r).toLowerCase();
        assertTrue(lower.contains("httponly") && lower.contains("secure") && lower.contains("samesite=strict"));
    }

    @Test
    void cookieValueIsNotTheUsername() {
        String name = uniqueUsername();
        registerPlayer(name);
        ApiClient c = loginPlayer(name);
        assertFalse(c.sessionCookie().contains(name));
    }

    @Test
    void tokensAreLongRandomAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            ApiClient c = registerPlayer();
            assertTrue(c.sessionCookie().length() >= 40, "token too short: " + c.sessionCookie().length());
            assertTrue(c.sessionCookie().matches("[A-Za-z0-9_-]+"));
            assertTrue(seen.add(c.sessionCookie()), "duplicate session token");
        }
    }

    @Test
    void logoutClearsTheCookie() {
        ApiClient p = registerPlayer();
        ApiClient.Response r = p.post("/api/auth/logout", map());
        String c = setCookie(r);
        assertNotNull(c);
        assertTrue(c.contains("Max-Age=0"), c);
        assertTrue(c.startsWith("BD_SESSION=;"), c);
    }

    @Test
    void aSecondLoginDoesNotInvalidateTheFirstButEachSessionIsIndependent() {
        String name = uniqueUsername();
        ApiClient first = registerPlayer(name);
        ApiClient second = loginPlayer(name);
        assertEquals(200, first.get("/api/me").status());
        assertEquals(200, second.get("/api/me").status());
        second.post("/api/auth/logout", map());
        assertEquals(200, first.get("/api/me").status(), "logging out one device must not log out the others");
    }

    @Test
    void forgedOrTruncatedCookiesAreRejected() {
        ApiClient real = registerPlayer();
        for (String forged : new String[]{"AAAA", "x".repeat(43), real.sessionCookie().substring(1), real.sessionCookie() + "A", "../..", ""}) {
            ApiClient c = anonymous();
            c.setSessionCookie(forged);
            assertEquals(401, c.get("/api/me").status(), "forged cookie accepted: " + forged);
        }
    }

    @Test
    void playerCookieDoesNotWorkAsAnAdminCookie() {
        ApiClient player = registerPlayer();
        assertEquals(403, player.get("/api/admin/bankroll").status());
    }
}
