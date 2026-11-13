package com.dworf.slots_demo.security;

import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Own application context with deliberately tiny limits (the shared context raises them to 100000).
 * The per-IP counters are shared inside this context, so only one test here uses the player login limiter.
 */
@Tag("security")
@DisplayName("Brute-force protection")
@TestPropertySource(properties = {
        "slots.limits.login-per-ip=5",
        "slots.limits.admin-login-per-ip=3"
})
class RateLimitTest extends IntegrationTestBase {

    @Test
    void telegramLoginFloodIsLimitedPerIp() {
        int unauthorized = 0;
        int limited = 0;
        for (int i = 0; i < 8; i++) {
            ApiClient.Response r = anonymous().post("/api/auth/telegram", map("initData", "hash=" + i));
            if (r.status() == 401) unauthorized++;
            if (r.status() == 429) limited++;
        }
        assertEquals(5, unauthorized, "the first 5 attempts are evaluated");
        assertEquals(3, limited, "everything above the budget is refused before any verification work");
    }

    @Test
    void adminLoginIsLimitedPerIp() {
        String validCode = Totp.code(ADMIN_TOTP_SECRET, Instant.now().getEpochSecond());
        for (int i = 0; i < 3; i++) {
            assertEquals(401, anonymous().post("/api/admin/auth/login",
                    map("username", ADMIN_USER, "password", "wrong-password-" + i, "totp", validCode)).status());
        }
        assertEquals(429, anonymous().post("/api/admin/auth/login",
                map("username", ADMIN_USER, "password", ADMIN_PASSWORD, "totp", validCode)).status(),
                "after 3 failed admin attempts even the correct credentials must wait");
    }
}
