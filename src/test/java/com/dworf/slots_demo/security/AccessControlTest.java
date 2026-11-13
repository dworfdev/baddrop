package com.dworf.slots_demo.security;

import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IDOR: the acting account always comes from the session, never from anything the client sends. */
@Tag("security")
@DisplayName("Access control / IDOR")
class AccessControlTest extends IntegrationTestBase {

    @Test
    void usernameInQueryStringIsIgnored() {
        String bobName = uniqueUsername();
        ApiClient bob = registerPlayer(bobName);
        fund(bob, "9.00");
        ApiClient alice = registerPlayer();

        Map<String, Object> me = alice.get("/api/me?username=" + bobName).json();
        assertFalse(bobName.equals(me.get("username")), "must return the caller's own profile");
        assertMoney("0.00", ApiClient.money(alice.get("/api/me/balance?username=" + bobName + "&password=x").json().get("balance")));
    }

    @Test
    void usernameInRequestBodyIsIgnoredOnMoneyAndGameEndpoints() {
        String bobName = uniqueUsername();
        ApiClient bob = registerPlayer(bobName);
        fund(bob, "20.00");
        ApiClient alice = registerPlayer();
        fund(alice, "20.00");
        String slug = (String) anonymous().get("/api/cases").jsonList().get(0).get("slug");

        ApiClient.Response r = alice.post("/api/me/cases/buy", map("username", bobName, "password", PLAYER_PASSWORD, "item", slug, "quantity", 1), idem());
        // whether the case is affordable or not, Bob must be untouched
        assertMoney("20.00", balanceOf(bob));
        assertTrue(((List<?>) bob.get("/api/me/items").json().get("items")).isEmpty());
        assertTrue(r.status() == 200 || r.status() == 400);

        alice.post("/api/me/withdraw", map("username", bobName, "amount", new BigDecimal("5.00"), "walletAddress", TON_WALLET), idem());
        assertMoney("20.00", balanceOf(bob));
    }

    @Test
    void aSessionCannotBeUsedToReadAnotherPlayersTransactions() {
        ApiClient bob = registerPlayer();
        ApiClient alice = registerPlayer();
        String bobTx = (String) bob.post("/api/me/deposit/start", map("amount", new BigDecimal("3.00")), idem()).json().get("txHash");
        assertEquals(409, alice.post("/api/me/deposit/confirm", map("txHash", bobTx)).status(),
                "confirming somebody else's deposit session must fail");
        assertEquals(true, bob.get("/api/me/deposit/active").json().get("active"), "Bob's session is untouched");
        assertEquals(false, alice.get("/api/me/deposit/active").json().get("active"));
    }

    @Test
    void playersCannotApproveTheirOwnDeposits() {
        ApiClient p = registerPlayer();
        String tx = (String) p.post("/api/me/deposit/start", map("amount", new BigDecimal("1000.00")), idem()).json().get("txHash");
        p.post("/api/me/deposit/confirm", map("txHash", tx));
        assertEquals(403, p.post("/api/admin/transactions/" + tx + "/approve", map("externalTxHash", "chain" + freshKey().replace("-", ""))).status());
        assertMoney("0.00", balanceOf(p));
    }

    @Test
    void sensitiveFieldsNeverAppearInAnyPlayerResponse() {
        ApiClient p = registerPlayer();
        fund(p, "10.00");
        for (String path : new String[]{"/api/me", "/api/auth/me", "/api/me/balance", "/api/me/items", "/api/me/deposit/active", "/api/leaderboard", "/api/live-feed"}) {
            String b = (path.startsWith("/api/leaderboard") || path.startsWith("/api/live-feed") ? anonymous() : p).get(path).body().toLowerCase();
            assertFalse(b.contains("passwordhash") || b.contains("password_hash") || b.contains("$2a$"), path);
        }
    }
}
