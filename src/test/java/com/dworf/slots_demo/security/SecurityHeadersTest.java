package com.dworf.slots_demo.security;

import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("security")
@DisplayName("Browser hardening headers")
class SecurityHeadersTest extends IntegrationTestBase {

    @Test
    void apiResponsesCarryTheBaselineHeaders() {
        ApiClient.Response r = anonymous().get("/api/config");
        assertEquals("nosniff", r.header("X-Content-Type-Options"));
        assertEquals("no-referrer", r.header("Referrer-Policy"));
        String csp = r.header("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("default-src 'self'"));
        assertTrue(csp.contains("script-src 'self' https://telegram.org"), "only the Telegram SDK may be loaded from outside");
        assertTrue(!csp.contains("'unsafe-inline'") || !csp.contains("script-src 'self' 'unsafe-inline'"));
        assertTrue(!csp.contains("'unsafe-eval'"));
        assertTrue(csp.contains("frame-ancestors") && csp.contains("telegram.org"), "the Mini App must be embeddable by Telegram");
        assertTrue(!csp.contains("frame-ancestors *"));
    }

    @Test
    void adminSurfaceIsNeverFramableAndLoadsNothingExternal() {
        ApiClient.Response r = anonymous().get("/api/admin/auth/me"); // 401, but headers still apply
        assertEquals("DENY", r.header("X-Frame-Options"));
        String csp = r.header("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertTrue(!csp.contains("telegram.org"));
        ApiClient.Response page = anonymous().get("/admin");
        assertEquals("DENY", page.header("X-Frame-Options"));
    }

    @Test
    void apiResponsesAreNeverCached() {
        assertEquals("no-store", anonymous().get("/api/config").header("Cache-Control"));
        assertEquals("no-store", registerPlayer().get("/api/me").header("Cache-Control"));
    }

    @Test
    void errorResponsesAlsoCarryTheHeaders() {
        ApiClient.Response r = anonymous().get("/api/me");
        assertEquals(401, r.status());
        assertEquals("nosniff", r.header("X-Content-Type-Options"));
    }

    @Test
    void staticFrontendIsServedWithTheCspToo() {
        ApiClient.Response r = anonymous().get("/app.js");
        assertEquals(200, r.status());
        assertNotNull(r.header("Content-Security-Policy"));
    }

    @Test
    void frontendNoLongerContainsCredentialHandlingFromTheOldDesign() {
        String js = anonymous().get("/app.js").body();
        assertTrue(!js.contains("sessionStorage.setItem"), "no secret may be written to sessionStorage");
        assertTrue(!js.contains("password: currentUser.password"));
        assertTrue(!js.contains("/api/users/"), "old credential-carrying routes must be gone from the client");
        assertTrue(!js.contains("/api/auth/register") && !js.contains("/api/auth/password"), "password flows are gone");
        String admin = anonymous().get("/admin.js").body();
        assertTrue(!admin.contains("X-Admin-Key"));
    }
}
