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
 * Each test uses fresh usernames, but the per-IP counters are shared inside this context, so the
 * budgets below are sized for the whole class: register 6, admin 3, per-user login 3, password check 3.
 */
@Tag("security")
@DisplayName("Brute-force protection")
@TestPropertySource(properties = {
        "slots.limits.login-per-ip=1000",
        "slots.limits.login-per-user-ip=3",
        "slots.limits.register-per-ip=6",
        "slots.limits.admin-login-per-ip=3",
        "slots.limits.password-check-per-user=3"
})
class RateLimitTest extends IntegrationTestBase {

    @Test
    void loginIsBlockedAfterRepeatedFailuresEvenWithTheCorrectPassword() {
        String name = uniqueUsername();
        registerPlayer(name);                                   // register budget: 1
        for (int i = 0; i < 3; i++) {
            assertEquals(401, anonymous().post("/api/auth/login", map("username", name, "password", "wrong-password-" + i)).status());
        }
        assertEquals(429, anonymous().post("/api/auth/login", map("username", name, "password", PLAYER_PASSWORD)).status(),
                "after 3 failures the account/IP pair must be locked out, whatever the password");
    }

    @Test
    void lockoutIsPerUsernameNotGlobal() {
        String locked = uniqueUsername();
        registerPlayer(locked);                                 // register budget: 2
        for (int i = 0; i < 3; i++) {
            anonymous().post("/api/auth/login", map("username", locked, "password", "wrong-password-" + i));
        }
        String other = uniqueUsername();
        registerPlayer(other);                                  // register budget: 3
        assertEquals(200, anonymous().post("/api/auth/login", map("username", other, "password", PLAYER_PASSWORD)).status(),
                "locking one username must not lock everybody else");
    }

    @Test
    void passwordGuessingThroughChangePasswordIsLimited() {
        ApiClient p = registerPlayer();                         // register budget: 4
        for (int i = 0; i < 3; i++) {
            assertEquals(403, p.post("/api/auth/password", map("currentPassword", "guess-number-" + i, "newPassword", "brand-new-passw0rd")).status());
        }
        assertEquals(429, p.post("/api/auth/password", map("currentPassword", PLAYER_PASSWORD, "newPassword", "brand-new-passw0rd")).status());
    }

    @Test
    void passwordGuessingThroughDeleteAccountIsLimited() {
        ApiClient p = registerPlayer();                         // register budget: 5
        for (int i = 0; i < 3; i++) {
            assertEquals(403, p.post("/api/auth/delete", map("password", "guess-number-" + i)).status());
        }
        assertEquals(429, p.post("/api/auth/delete", map("password", PLAYER_PASSWORD)).status());
    }

    @Test
    void registrationFloodIsLimitedPerIp() {
        int accepted = 0;
        int limited = 0;
        for (int i = 0; i < 4; i++) {
            ApiClient.Response r = anonymous().post("/api/auth/register", map("username", uniqueUsername(), "password", PLAYER_PASSWORD));
            if (r.status() == 201) accepted++;
            if (r.status() == 429) limited++;
        }
        // the budget of 6 is shared with the tests above, so at least one of these four must be refused
        org.junit.jupiter.api.Assertions.assertTrue(limited >= 1, "accepted=" + accepted + " limited=" + limited);
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
