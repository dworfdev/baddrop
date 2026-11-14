package com.dworf.slots_demo.bankroll;

import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Monthly close: the owner takes house profit out of the wallet and records it; the prize pool is never touched. */
@Tag("bankroll")
@DisplayName("Monthly settlement of house profit")
class SettlementTest extends IntegrationTestBase {

    private BigDecimal houseProfit() {
        return ApiClient.money(admin().get("/api/admin/bankroll").json().get("houseProfit"));
    }

    private BigDecimal prizePool() {
        return ApiClient.money(admin().get("/api/admin/bankroll").json().get("profitPool"));
    }

    private Map<String, Object> report() {
        return admin().get("/api/admin/reconciliation").json();
    }

    /** Plays some real cases so that house profit is positive. */
    private void accrueHouseProfit() {
        Map<String, Object> c = anonymous().get("/api/cases").jsonList().stream()
                .min((x, y) -> ApiClient.money(x.get("price")).compareTo(ApiClient.money(y.get("price")))).orElseThrow();
        String slug = (String) c.get("slug");
        BigDecimal price = ApiClient.money(c.get("price"));
        ApiClient p = registerPlayer();
        fund(p, price.multiply(BigDecimal.valueOf(10)).toPlainString());
        p.post("/api/me/cases/buy", map("item", slug, "quantity", 5), idem());
        p.post("/api/me/cases/open", map("item", slug, "quantity", 5));
        assertTrue(houseProfit().signum() > 0, "test setup: house profit must be positive");
    }

    @Test
    void onlyAdminsCanSettle() {
        assertEquals(401, anonymous().post("/api/admin/bankroll/settle", map("amount", new BigDecimal("1"))).status());
        assertEquals(403, registerPlayer().post("/api/admin/bankroll/settle", map("amount", new BigDecimal("1"))).status());
        assertEquals(401, anonymous().get("/api/admin/owner-draws").status());
        assertEquals(403, registerPlayer().get("/api/admin/owner-draws").status());
    }

    @Test
    void invalidAmountsAreRejected() {
        accrueHouseProfit();
        ApiClient a = admin();
        assertEquals(400, a.post("/api/admin/bankroll/settle", map()).status());
        assertEquals(400, a.post("/api/admin/bankroll/settle", map("amount", BigDecimal.ZERO)).status());
        assertEquals(400, a.post("/api/admin/bankroll/settle", map("amount", new BigDecimal("-1"))).status());
        assertEquals(400, a.post("/api/admin/bankroll/settle", map("amount", new BigDecimal("0.00001"))).status(), "max 4 decimals");
        assertEquals(409, a.post("/api/admin/bankroll/settle", map("amount", houseProfit().add(BigDecimal.ONE))).status(),
                "cannot take out more than the house profit");
    }

    @Test
    void settlingMovesOnlyHouseProfitKeepsTheBooksBalancedAndLowersTheExpectedWallet() {
        accrueHouseProfit();
        BigDecimal profitBefore = houseProfit();
        BigDecimal poolBefore = prizePool();
        Map<String, Object> before = report();
        BigDecimal take = profitBefore.divide(BigDecimal.valueOf(2), 4, java.math.RoundingMode.DOWN);
        assertTrue(take.signum() > 0);

        ApiClient.Response r = admin().post("/api/admin/bankroll/settle", map("amount", take, "note", "month close"));
        assertEquals(200, r.status(), r.body());

        assertEquals(0, profitBefore.subtract(take).compareTo(houseProfit()));
        assertEquals(0, poolBefore.compareTo(prizePool()), "the prize pool must never change on settlement");
        Map<String, Object> after = report();
        assertEquals(0, ApiClient.money(before.get("difference")).compareTo(ApiClient.money(after.get("difference"))),
                "the books must not drift (the shared DB may carry an offset from the reset test, so compare to the baseline): " + after);
        assertEquals(0, ApiClient.money(before.get("expectedWalletBalance")).subtract(take)
                .compareTo(ApiClient.money(after.get("expectedWalletBalance"))));
        assertEquals(0, ApiClient.money(before.get("ownerDraws")).add(take).compareTo(ApiClient.money(after.get("ownerDraws"))));
    }

    @Test
    void settlementIsListedAndAudited() {
        accrueHouseProfit();
        BigDecimal take = new BigDecimal("0.0001");
        assertEquals(200, admin().post("/api/admin/bankroll/settle", map("amount", take, "note", "audit me")).status());
        assertTrue(admin().get("/api/admin/owner-draws").jsonList().stream()
                .anyMatch(d -> "audit me".equals(d.get("note")) && ADMIN_USER.equals(d.get("adminName"))));
        assertTrue(admin().get("/api/admin/audit").jsonList().stream()
                .anyMatch(a -> "HOUSE_PROFIT_SETTLED".equals(a.get("action")) && ADMIN_USER.equals(a.get("actor"))));
    }

    @Test
    void settlingEverythingLeavesZeroAndCannotBeRepeated() {
        accrueHouseProfit();
        BigDecimal all = houseProfit();
        BigDecimal diff = ApiClient.money(report().get("difference"));
        assertEquals(200, admin().post("/api/admin/bankroll/settle", map("amount", all)).status());
        assertEquals(0, houseProfit().signum());
        assertEquals(409, admin().post("/api/admin/bankroll/settle", map("amount", new BigDecimal("0.0001"))).status());
        assertEquals(0, diff.compareTo(ApiClient.money(report().get("difference"))));
    }

    @Test
    void parallelSettlementsCannotOverdrawHouseProfit() {
        accrueHouseProfit();
        BigDecimal all = houseProfit();
        BigDecimal diff = ApiClient.money(report().get("difference"));
        var rs = com.dworf.slots_demo.support.Concurrently.runEach(6, i -> admin().post("/api/admin/bankroll/settle", map("amount", all)));
        assertEquals(1, com.dworf.slots_demo.support.Concurrently.count(rs, 200), rs.stream().map(ApiClient.Response::status).toList().toString());
        assertTrue(houseProfit().signum() >= 0);
        assertEquals(0, diff.compareTo(ApiClient.money(report().get("difference"))));
    }

    @Test
    void deductingFromThePrizePoolIsAdminOnlyBoundedAndKeepsTheBooksConsistent() {
        accrueHouseProfit();
        assertEquals(401, anonymous().post("/api/admin/bankroll/deduct-pool", map("amount", new BigDecimal("1"))).status());
        assertEquals(403, registerPlayer().post("/api/admin/bankroll/deduct-pool", map("amount", new BigDecimal("1"))).status());
        ApiClient a = admin();
        assertEquals(400, a.post("/api/admin/bankroll/deduct-pool", map("amount", BigDecimal.ZERO)).status());
        assertEquals(409, a.post("/api/admin/bankroll/deduct-pool", map("amount", prizePool().add(BigDecimal.ONE))).status());

        BigDecimal poolBefore = prizePool();
        BigDecimal profitBefore = houseProfit();
        Map<String, Object> before = report();
        BigDecimal take = new BigDecimal("0.5000");
        ApiClient.Response r = a.post("/api/admin/bankroll/deduct-pool", map("amount", take, "note", "approved by mistake"));
        assertEquals(200, r.status(), r.body());
        assertEquals(0, poolBefore.subtract(take).compareTo(prizePool()));
        assertEquals(0, profitBefore.compareTo(houseProfit()), "house profit must not change");
        Map<String, Object> after = report();
        assertEquals(0, ApiClient.money(before.get("difference")).compareTo(ApiClient.money(after.get("difference"))));
        assertEquals(0, ApiClient.money(before.get("expectedWalletBalance")).subtract(take)
                .compareTo(ApiClient.money(after.get("expectedWalletBalance"))));
        assertTrue(a.get("/api/admin/owner-draws").jsonList().stream()
                .anyMatch(d -> "POOL".equals(d.get("source")) && "approved by mistake".equals(d.get("note"))));
        assertTrue(a.get("/api/admin/audit").jsonList().stream().anyMatch(x -> "PRIZE_POOL_DEDUCTED".equals(x.get("action"))));
    }

    @Test
    void publicConfigExposesTheBotLinkAndNoDevLoginInProdLikeSetup() {
        Map<String, Object> cfg = anonymous().get("/api/config").json();
        assertTrue(cfg.containsKey("telegramBotUrl"));
        assertEquals(false, cfg.get("devLoginEnabled"));
    }
}
