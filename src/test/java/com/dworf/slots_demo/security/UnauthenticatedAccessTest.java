package com.dworf.slots_demo.security;

import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Access matrix. C1 regression: before the redesign most of these answered 200 to anybody who knew
 * (or simply omitted) a username. Every non-public endpoint must now answer 401 without a session.
 */
@Tag("security")
@DisplayName("Default-deny: unauthenticated access matrix")
class UnauthenticatedAccessTest extends IntegrationTestBase {

    static Stream<Arguments> protectedEndpoints() {
        return Stream.of(
                Arguments.of("GET", "/api/me"),
                Arguments.of("GET", "/api/me/balance"),
                Arguments.of("GET", "/api/me/items"),
                Arguments.of("GET", "/api/me/deposit/active"),
                Arguments.of("GET", "/api/auth/me"),
                Arguments.of("POST", "/api/auth/password"),
                Arguments.of("POST", "/api/auth/delete"),
                Arguments.of("POST", "/api/me/deposit/start"),
                Arguments.of("POST", "/api/me/deposit/confirm"),
                Arguments.of("POST", "/api/me/withdraw"),
                Arguments.of("POST", "/api/me/cases/buy"),
                Arguments.of("POST", "/api/me/cases/open"),
                Arguments.of("POST", "/api/me/cases/instant-open"),
                Arguments.of("GET", "/api/admin/transactions/pending"),
                Arguments.of("POST", "/api/admin/transactions/abc/approve"),
                Arguments.of("POST", "/api/admin/transactions/abc/reject"),
                Arguments.of("GET", "/api/admin/bankroll"),
                Arguments.of("POST", "/api/admin/bankroll/reset"),
                Arguments.of("GET", "/api/admin/reconciliation"),
                Arguments.of("GET", "/api/admin/audit"),
                Arguments.of("GET", "/api/admin/users/someone"),
                Arguments.of("GET", "/api/admin/auth/me"));
    }

    @ParameterizedTest(name = "{0} {1} -> 401")
    @MethodSource("protectedEndpoints")
    void rejectsAnonymousCallers(String method, String path) {
        ApiClient.Response r = anonymous().send(method, path, "POST".equals(method) ? "{}" : null, java.util.Map.of(), false);
        assertEquals(401, r.status(), method + " " + path + " must require authentication, got " + r.status() + " " + r.body());
    }

    @ParameterizedTest(name = "legacy {0} {1} is gone")
    @MethodSource("legacyEndpoints")
    void legacyCredentialEndpointsNoLongerGrantAnything(String method, String path) {
        ApiClient.Response r = anonymous().send(method, path, "POST".equals(method) ? "{\"username\":\"x\",\"password\":\"y\"}" : null, java.util.Map.of(), false);
        assertNotEquals(200, r.status(), "legacy endpoint still answers 200: " + method + " " + path);
        assertTrue(r.status() >= 400);
    }

    static Stream<Arguments> legacyEndpoints() {
        return Stream.of(
                Arguments.of("GET", "/api/users"),
                Arguments.of("GET", "/api/users/top"),
                Arguments.of("GET", "/api/users/user?username=admin&password="),
                Arguments.of("GET", "/api/users/balance?username=admin"),
                Arguments.of("GET", "/api/users/items?username=admin"),
                Arguments.of("POST", "/api/users/login"),
                Arguments.of("POST", "/api/users/items/buy"),
                Arguments.of("POST", "/api/users/promo"),
                Arguments.of("POST", "/api/admin/login"),
                Arguments.of("POST", "/api/cases/instant-open"));
    }

    @ParameterizedTest(name = "public GET {0} -> 200")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/api", "/api/config", "/api/cases", "/api/cases/math", "/api/live-feed", "/api/leaderboard"})
    void publicEndpointsStayPublic(String path) {
        assertEquals(200, anonymous().get(path).status());
    }

    @Test
    void publicConfigDoesNotExposeSecrets() {
        String body = anonymous().get("/api/config").body().toLowerCase();
        assertTrue(!body.contains("password") && !body.contains("secret") && !body.contains("totp") && !body.contains("hash"));
    }

    @ParameterizedTest(name = "path trick {0} is refused")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/api/admin/../me", "/api//admin/bankroll", "/api/admin/bankroll;x=1", "/api/admin/bankroll%2f", "/api/admin%2fbankroll", "/api/admin\\bankroll"})
    void pathNormalisationTricksDoNotBypassTheGate(String path) {
        int status = anonymous().get(path).status();
        assertTrue(status == 400 || status == 401 || status == 403 || status == 404, path + " -> " + status);
    }

    @Test
    void adminPageRedirectsAnonymousVisitorsToTheLoginPage() {
        for (String p : new String[]{"/admin", "/admin.html"}) {
            ApiClient.Response r = anonymous().get(p);
            assertEquals(302, r.status(), p);
            assertEquals("/admin-login.html", r.header("Location"));
        }
    }

    @Test
    void adminPageRedirectsPlayersToo() {
        ApiClient player = registerPlayer();
        assertEquals(302, player.get("/admin").status());
    }
}
