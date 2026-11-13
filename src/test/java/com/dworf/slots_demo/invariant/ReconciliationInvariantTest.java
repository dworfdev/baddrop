package com.dworf.slots_demo.invariant;

import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.Concurrently;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H15: sum(balances) + inventory value + pending withdrawals + prizePool + houseProfit - seed
 *      == approved deposits - approved withdrawals.
 * Other test classes spin the bankroll directly (no player money), so the absolute difference is not zero in a shared
 * database. What MUST hold is that player-facing operations never change it: they move money, they never create or lose it.
 */
@Tag("invariant")
@DisplayName("Ledger reconciliation invariant")
class ReconciliationInvariantTest extends IntegrationTestBase {

    private BigDecimal difference() {
        ApiClient.Response r = admin().get("/api/admin/reconciliation");
        assertEquals(200, r.status(), r.body());
        return ApiClient.money(r.json().get("difference"));
    }

    private Map<String, Object> cheapestCase() {
        return anonymous().get("/api/cases").jsonList().stream()
                .min((x, y) -> ApiClient.money(x.get("price")).compareTo(ApiClient.money(y.get("price")))).orElseThrow();
    }

    private void assertUnchanged(BigDecimal baseline, String after) {
        BigDecimal now = difference();
        assertEquals(0, baseline.compareTo(now), "the books drifted after " + after + ": baseline=" + baseline + " now=" + now);
    }

    @Test
    void depositApprovalAndRejectionKeepTheBooksBalanced() {
        BigDecimal base = difference();
        ApiClient p = registerPlayer();
        fund(p, "25.00");
        assertUnchanged(base, "an approved deposit");

        String tx = (String) p.post("/api/me/deposit/start", map("amount", new BigDecimal("8.00")), idem()).json().get("txHash");
        p.post("/api/me/deposit/confirm", map("txHash", tx, "receipt", freshReceipt()));
        assertUnchanged(base, "a pending deposit");
        admin().post("/api/admin/transactions/" + tx + "/reject", map());
        assertUnchanged(base, "a rejected deposit");
    }

    @Test
    void withdrawalPendingApprovedAndRejectedKeepTheBooksBalanced() {
        BigDecimal base = difference();
        ApiClient p = registerPlayer();
        fund(p, "30.00");
        String username = (String) p.get("/api/me").json().get("username");

        p.post("/api/me/withdraw", map("amount", new BigDecimal("10.00"), "walletAddress", TON_WALLET), idem());
        assertUnchanged(base, "a pending withdrawal");
        String tx1 = pendingHash(username);
        admin().post("/api/admin/transactions/" + tx1 + "/reject", map());
        assertUnchanged(base, "a rejected withdrawal");

        p.post("/api/me/withdraw", map("amount", new BigDecimal("12.00"), "walletAddress", TON_WALLET), idem());
        String tx2 = pendingHash(username);
        admin().post("/api/admin/transactions/" + tx2 + "/approve", map());
        assertUnchanged(base, "an approved withdrawal");
    }

    @Test
    void buyingAndOpeningCasesKeepTheBooksBalanced() {
        BigDecimal base = difference();
        Map<String, Object> c = cheapestCase();
        String slug = (String) c.get("slug");
        BigDecimal price = ApiClient.money(c.get("price"));
        ApiClient p = registerPlayer();
        fund(p, price.multiply(BigDecimal.valueOf(20)).toPlainString());

        p.post("/api/me/cases/buy", map("item", slug, "quantity", 5), idem());
        assertUnchanged(base, "buying 5 cases (value moves from balance to inventory)");
        p.post("/api/me/cases/open", map("item", slug, "quantity", 3));
        assertUnchanged(base, "opening 3 owned cases");
        p.post("/api/me/cases/instant-open", map("item", slug, "quantity", 5), idem());
        assertUnchanged(base, "an instant open");
        p.post("/api/me/cases/open", map("item", slug, "quantity", 2));
        assertUnchanged(base, "opening the remaining cases");
    }

    @Test
    void manyOpeningsStayExactToTheCent() {
        BigDecimal base = difference();
        Map<String, Object> c = cheapestCase();
        String slug = (String) c.get("slug");
        BigDecimal price = ApiClient.money(c.get("price"));
        ApiClient p = registerPlayer();
        fund(p, price.multiply(BigDecimal.valueOf(100)).toPlainString());
        for (int i = 0; i < 40; i++) {
            ApiClient.Response r = p.post("/api/me/cases/instant-open", map("item", slug, "quantity", 5), idem());
            if (r.status() != 200) break; // ran out of money; that is fine
        }
        assertUnchanged(base, "many instant openings");
    }

    @Test
    void concurrentMixedLoadKeepsTheBooksBalanced() {
        BigDecimal base = difference();
        Map<String, Object> c = cheapestCase();
        String slug = (String) c.get("slug");
        BigDecimal price = ApiClient.money(c.get("price"));

        List<ApiClient> players = Concurrently.runEach(6, i -> registerPlayer());
        for (ApiClient p : players) fund(p, price.multiply(BigDecimal.valueOf(15)).max(new BigDecimal("5.00")).toPlainString());

        Concurrently.runEach(players.size() * 3, i -> {
            ApiClient p = players.get(i % players.size());
            return switch (i % 3) {
                case 0 -> p.post("/api/me/cases/instant-open", map("item", slug, "quantity", 2), idem());
                case 1 -> p.post("/api/me/cases/buy", map("item", slug, "quantity", 2), idem());
                default -> p.post("/api/me/withdraw", map("amount", new BigDecimal("2.50"), "walletAddress", TON_WALLET), idem());
            };
        });
        Concurrently.runEach(players.size(), i -> players.get(i).post("/api/me/cases/open", map("item", slug, "quantity", 1)));
        assertUnchanged(base, "a concurrent mix of opens, buys and withdrawals");
    }

    @Test
    void noBalanceEverGoesNegativeAndThePoolStaysNonNegative() {
        ApiClient.Response b = admin().get("/api/admin/bankroll");
        assertTrue(ApiClient.money(b.json().get("profitPool")).signum() >= 0);
        assertTrue(ApiClient.money(b.json().get("houseProfit")).signum() >= 0);
        ApiClient.Response rec = admin().get("/api/admin/reconciliation");
        assertTrue(ApiClient.money(rec.json().get("balances")).signum() >= 0);
    }

    private String pendingHash(String username) {
        return (String) admin().get("/api/admin/transactions/pending").jsonList().stream()
                .filter(t -> username.equals(t.get("username"))).findFirst().orElseThrow().get("txHash");
    }
}
