package com.dworf.slots_demo.integration;

import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Account lifecycle: register / login / logout / password / delete")
class AuthFlowTest extends IntegrationTestBase {

    @Test
    void registerCreatesAccountWithZeroBalanceAndLogsIn() {
        String name = uniqueUsername();
        ApiClient c = anonymous();
        ApiClient.Response r = c.post("/api/auth/register", map("username", name, "password", PLAYER_PASSWORD));
        assertEquals(201, r.status(), r.body());
        Map<String, Object> user = (Map<String, Object>) r.json().get("user");
        assertEquals(name, user.get("username"));
        assertMoney("0.00", ApiClient.money(user.get("balance")));
        assertNotNull(r.json().get("csrfToken"));
        assertNotNull(c.sessionCookie(), "register must issue a session cookie");
        assertEquals(200, c.get("/api/me").status());
    }

    @Test
    void responsesNeverContainPasswordOrHash() {
        ApiClient c = registerPlayer();
        for (String path : new String[]{"/api/me", "/api/auth/me", "/api/me/balance", "/api/me/items"}) {
            String body = c.get(path).body().toLowerCase();
            assertFalse(body.contains("password"), path + " leaked a password field: " + body);
            assertFalse(body.contains("$2a$") || body.contains("$2b$"), path + " leaked a hash");
        }
    }

    @Test
    void usernameIsCaseInsensitiveAndUnique() {
        String name = uniqueUsername();
        registerPlayer(name);
        ApiClient.Response dup = anonymous().post("/api/auth/register", map("username", name.toUpperCase(), "password", PLAYER_PASSWORD));
        assertEquals(409, dup.status(), dup.body());
    }

    @Test
    void weakPasswordsAreRejected() {
        for (String weak : new String[]{"", "short", "admin1234", "aaaaaaaaaaaa"}) {
            ApiClient.Response r = anonymous().post("/api/auth/register", map("username", uniqueUsername(), "password", weak));
            assertEquals(400, r.status(), "weak password accepted: '" + weak + "'");
        }
    }

    @Test
    void loginWithCorrectPasswordWorksWithWrongOrBlankFails() {
        String name = uniqueUsername();
        registerPlayer(name);
        assertEquals(200, anonymous().post("/api/auth/login", map("username", name, "password", PLAYER_PASSWORD)).status());
        assertEquals(401, anonymous().post("/api/auth/login", map("username", name, "password", "wrong-password-1")).status());
        // C1 regression (auth was optional): a blank password used to skip the check entirely
        assertEquals(401, anonymous().post("/api/auth/login", map("username", name, "password", "")).status());
        assertEquals(401, anonymous().post("/api/auth/login", map("username", name)).status());
    }

    @Test
    void unknownUserAndWrongPasswordLookIdentical() {
        String name = uniqueUsername();
        registerPlayer(name);
        ApiClient.Response wrongPw = anonymous().post("/api/auth/login", map("username", name, "password", "wrong-password-1"));
        ApiClient.Response noUser = anonymous().post("/api/auth/login", map("username", uniqueUsername(), "password", "wrong-password-1"));
        assertEquals(wrongPw.status(), noUser.status());
        assertEquals(wrongPw.message(), noUser.message(), "messages must not reveal which usernames exist");
    }

    @Test
    void logoutRevokesTheSessionServerSide() {
        ApiClient c = registerPlayer();
        String stolen = c.sessionCookie();
        assertEquals(200, c.post("/api/auth/logout", map()).status());
        ApiClient replay = anonymous();
        replay.setSessionCookie(stolen);
        assertEquals(401, replay.get("/api/me").status(), "a logged-out cookie must be dead even if someone kept a copy");
    }

    @Test
    void changePasswordRequiresCurrentPasswordAndRevokesOtherSessions() {
        String name = uniqueUsername();
        ApiClient first = registerPlayer(name);
        ApiClient second = loginPlayer(name, PLAYER_PASSWORD);

        assertEquals(403, first.post("/api/auth/password", map("currentPassword", "not-my-password", "newPassword", "brand-new-passw0rd")).status());
        ApiClient.Response ok = first.post("/api/auth/password", map("currentPassword", PLAYER_PASSWORD, "newPassword", "brand-new-passw0rd"));
        assertEquals(200, ok.status(), ok.body());
        first.setCsrf((String) ok.json().get("csrfToken"));

        assertEquals(200, first.get("/api/me").status(), "the session that changed the password stays valid");
        assertEquals(401, second.get("/api/me").status(), "every other session must be revoked");
        assertEquals(401, anonymous().post("/api/auth/login", map("username", name, "password", PLAYER_PASSWORD)).status());
        assertEquals(200, anonymous().post("/api/auth/login", map("username", name, "password", "brand-new-passw0rd")).status());
    }

    @Test
    void changePasswordRejectsSamePasswordAndWeakPassword() {
        ApiClient c = registerPlayer();
        assertEquals(400, c.post("/api/auth/password", map("currentPassword", PLAYER_PASSWORD, "newPassword", PLAYER_PASSWORD)).status());
        assertEquals(400, c.post("/api/auth/password", map("currentPassword", PLAYER_PASSWORD, "newPassword", "short")).status());
    }

    @Test
    void deleteAccountIsSoftAndFreesNothing() {
        String name = uniqueUsername();
        ApiClient c = registerPlayer(name);
        assertEquals(403, c.post("/api/auth/delete", map("password", "nope-nope-nope")).status());
        assertEquals(200, c.post("/api/auth/delete", map("password", PLAYER_PASSWORD)).status());

        assertEquals(401, anonymous().post("/api/auth/login", map("username", name, "password", PLAYER_PASSWORD)).status(),
                "a deleted account cannot log in");
        assertEquals(409, anonymous().post("/api/auth/register", map("username", name, "password", PLAYER_PASSWORD)).status(),
                "a deleted username stays reserved so history cannot be hijacked");
    }

    @Test
    void deleteAccountIsRefusedWhileMoneyIsLeft() {
        ApiClient c = registerPlayer();
        assertEquals(200, fund(c, "5.00").status());
        ApiClient.Response r = c.post("/api/auth/delete", map("password", PLAYER_PASSWORD));
        assertEquals(409, r.status(), r.body());
        assertTrue(r.message().toLowerCase().contains("balance"));
        assertEquals(200, c.get("/api/me").status(), "the account must still exist");
    }

    @Test
    void meWithoutCookieIsUnauthorized() {
        assertEquals(401, anonymous().get("/api/auth/me").status());
        assertNull(anonymous().sessionCookie());
    }
}
