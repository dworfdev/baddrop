package com.dworf.slots_demo.integration;

import com.dworf.slots_demo.security.TelegramInitData;
import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Account lifecycle: Telegram login / logout / delete")
class AuthFlowTest extends IntegrationTestBase {

    @Test
    void firstTelegramLoginCreatesAccountWithZeroBalanceAndSession() {
        String name = uniqueUsername();
        ApiClient c = anonymous();
        ApiClient.Response r = c.post("/api/auth/telegram", map("initData", initData(7_100_000_001L, name)));
        assertEquals(200, r.status(), r.body());
        Map<String, Object> user = (Map<String, Object>) r.json().get("user");
        assertEquals(name, user.get("username"));
        assertMoney("0.00", ApiClient.money(user.get("balance")));
        assertNotNull(r.json().get("csrfToken"));
        assertNotNull(c.sessionCookie(), "login must issue a session cookie");
        c.setCsrf((String) r.json().get("csrfToken"));
        assertEquals(200, c.get("/api/me").status());
    }

    @Test
    void sameTelegramUserGetsTheSameAccountEverySession() {
        String name = uniqueUsername();
        ApiClient first = registerPlayer(name);
        ApiClient second = loginPlayer(name);
        assertEquals(name, ((Map<String, Object>) second.get("/api/auth/me").json().get("user")).get("username"));
        assertEquals(200, first.get("/api/me").status(), "a second login must not kill the first session");
    }

    @Test
    void renamingOnTelegramDoesNotCreateASecondAccount() {
        String name = uniqueUsername();
        registerPlayer(name);
        long id = 7_200_000_000L + (System.nanoTime() % 1_000_000);
        ApiClient a = loginTelegram(id, name);
        ApiClient b = loginTelegram(id, uniqueUsername()); // same Telegram id, new @username
        assertEquals(((Map<String, Object>) a.get("/api/auth/me").json().get("user")).get("username"),
                ((Map<String, Object>) b.get("/api/auth/me").json().get("user")).get("username"));
    }

    @Test
    void forgedOrTamperedInitDataIsRejected() {
        String name = uniqueUsername();
        long id = 7_300_000_001L;
        String good = initData(id, name);
        assertEquals(401, anonymous().post("/api/auth/telegram", map("initData", good.replace(name, name + "x"))).status(), "tampered field");
        assertEquals(401, anonymous().post("/api/auth/telegram", map("initData", good.replaceAll("hash=[0-9a-f]+", "hash=" + "0".repeat(64)))).status(), "bad hash");
        String wrongToken = TelegramInitData.sign("999999:OTHER-TOKEN", id, name, "Test", Instant.now().getEpochSecond());
        assertEquals(401, anonymous().post("/api/auth/telegram", map("initData", wrongToken)).status(), "signed with another bot token");
        assertEquals(401, anonymous().post("/api/auth/telegram", map("initData", "")).status());
        assertEquals(401, anonymous().post("/api/auth/telegram", map("initData", "user=%7B%22id%22%3A1%7D")).status(), "no hash");
    }

    @Test
    void staleInitDataIsRejected() {
        String old = TelegramInitData.sign(BOT_TOKEN, 7_400_000_001L, uniqueUsername(), "Test", Instant.now().getEpochSecond() - 7 * 3600);
        assertEquals(401, anonymous().post("/api/auth/telegram", map("initData", old)).status());
    }

    @Test
    void failedLoginsLeaveGenericMessageAndNoSession() {
        ApiClient c = anonymous();
        ApiClient.Response r = c.post("/api/auth/telegram", map("initData", "hash=00"));
        assertEquals(401, r.status());
        assertNull(c.sessionCookie());
        assertFalse(r.body().toLowerCase().contains("hmac"), "no verification details leaked");
    }

    @Test
    void devLoginIsNotFoundWhenDisabled() {
        assertEquals(404, anonymous().post("/api/auth/dev-login", map("username", uniqueUsername())).status());
    }

    @Test
    void oldPasswordEndpointsAreGone() {
        assertTrue(anonymous().post("/api/auth/register", map("username", uniqueUsername(), "password", "whatever-123")).status() >= 400);
        assertTrue(anonymous().post("/api/auth/login", map("username", "x", "password", "y")).status() >= 400);
    }

    @Test
    void responsesNeverContainSecrets() {
        ApiClient c = registerPlayer();
        for (String path : new String[]{"/api/me", "/api/auth/me", "/api/me/balance", "/api/me/items"}) {
            String body = c.get(path).body().toLowerCase();
            assertFalse(body.contains("password"), path + " leaked a password field: " + body);
            assertFalse(body.contains("telegramid") || body.contains("telegram_id"), path + " exposes the Telegram id");
        }
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
    void deleteAccountNeedsMatchingUsernameConfirmation() {
        String name = uniqueUsername();
        ApiClient c = registerPlayer(name);
        assertEquals(400, c.post("/api/auth/delete", map("confirm", "not-my-name")).status());
        assertEquals(400, c.post("/api/auth/delete", map()).status());
        assertEquals(200, c.post("/api/auth/delete", map("confirm", name)).status());
        assertEquals(401, c.get("/api/me").status(), "the deleting session is dead");
    }

    @Test
    void deleteAccountIsRefusedWhileMoneyIsLeft() {
        String name = uniqueUsername();
        ApiClient c = registerPlayer(name);
        assertEquals(200, fund(c, "5.00").status());
        ApiClient.Response r = c.post("/api/auth/delete", map("confirm", name));
        assertEquals(409, r.status(), r.body());
        assertTrue(r.message().toLowerCase().contains("balance"));
        assertEquals(200, c.get("/api/me").status(), "the account must still exist");
    }

    @Test
    void deletedAccountIsReactivatedWithTheSameUsernameOnNextLogin() {
        String name = uniqueUsername();
        ApiClient c = registerPlayer(name);
        assertEquals(200, c.post("/api/auth/delete", map("confirm", name)).status());
        ApiClient again = loginPlayer(name);
        assertEquals(name, ((Map<String, Object>) again.get("/api/auth/me").json().get("user")).get("username"));
    }

    @Test
    void meWithoutCookieIsUnauthorized() {
        assertEquals(401, anonymous().get("/api/auth/me").status());
        assertNull(anonymous().sessionCookie());
    }
}
