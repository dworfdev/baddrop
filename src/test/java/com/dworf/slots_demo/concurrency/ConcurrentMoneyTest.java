package com.dworf.slots_demo.concurrency;

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

/** Race conditions on money: C5 (no locks on buy/open), double-credit, double-spend, replayed requests. */
@Tag("concurrency")
@DisplayName("Concurrent money operations")
class ConcurrentMoneyTest extends IntegrationTestBase {

    private static final int THREADS = 10;

    @Test
    void parallelWithdrawalsWithDifferentKeysSucceedExactlyOnce() {
        ApiClient p = registerPlayer();
        fund(p, "10.00");
        List<ApiClient.Response> rs = Concurrently.run(THREADS, () ->
                p.post("/api/me/withdraw", map("amount", new BigDecimal("6.00"), "walletAddress", TON_WALLET), idem()));
        assertEquals(1, Concurrently.count(rs, 200), "exactly one withdrawal may win: " + rs.stream().map(ApiClient.Response::status).toList());
        assertEquals(THREADS - 1, Concurrently.count(rs, 409) + Concurrently.count(rs, 400));
        assertMoney("4.00", balanceOf(p));
    }

    @Test
    void parallelWithdrawalsWithTheSameKeySucceedExactlyOnce() {
        ApiClient p = registerPlayer();
        fund(p, "10.00");
        Map<String, String> key = idem();
        List<ApiClient.Response> rs = Concurrently.run(THREADS, () ->
                p.post("/api/me/withdraw", map("amount", new BigDecimal("3.00"), "walletAddress", TON_WALLET), key));
        assertEquals(1, Concurrently.count(rs, 200));
        assertMoney("7.00", balanceOf(p));
    }

    @Test
    void parallelDepositConfirmationsPromoteTheSessionOnce() {
        ApiClient p = registerPlayer();
        String username = (String) p.get("/api/me").json().get("username");
        String tx = (String) p.post("/api/me/deposit/start", map("amount", new BigDecimal("5.00")), idem()).json().get("txHash");
        List<ApiClient.Response> rs = Concurrently.run(THREADS, () -> p.post("/api/me/deposit/confirm", map("txHash", tx)));
        assertEquals(1, Concurrently.count(rs, 200), rs.stream().map(ApiClient.Response::status).toList().toString());
        long pendingForUser = admin().get("/api/admin/transactions/pending").jsonList().stream()
                .filter(t -> username.equals(t.get("username"))).count();
        assertEquals(1, pendingForUser);
    }

    @Test
    void parallelApprovalsCreditTheDepositExactlyOnce() {
        ApiClient p = registerPlayer();
        String tx = (String) p.post("/api/me/deposit/start", map("amount", new BigDecimal("5.00")), idem()).json().get("txHash");
        p.post("/api/me/deposit/confirm", map("txHash", tx));
        List<ApiClient.Response> rs = Concurrently.runEach(THREADS, i -> admin().post("/api/admin/transactions/" + tx + "/approve",
                map("externalTxHash", "chain" + freshKey().replace("-", ""))));
        assertEquals(1, Concurrently.count(rs, 200), rs.stream().map(ApiClient.Response::status).toList().toString());
        assertMoney("5.00", balanceOf(p));
    }

    @Test
    void sameOnChainHashRacingAcrossTwoDepositsCreditsOnlyOne() {
        ApiClient a = registerPlayer();
        ApiClient b = registerPlayer();
        String txA = (String) a.post("/api/me/deposit/start", map("amount", new BigDecimal("5.00")), idem()).json().get("txHash");
        String txB = (String) b.post("/api/me/deposit/start", map("amount", new BigDecimal("5.00")), idem()).json().get("txHash");
        a.post("/api/me/deposit/confirm", map("txHash", txA));
        b.post("/api/me/deposit/confirm", map("txHash", txB));
        String sameChainHash = "chain" + freshKey().replace("-", "");
        List<ApiClient.Response> rs = Concurrently.runEach(2, i -> admin().post("/api/admin/transactions/" + (i == 0 ? txA : txB) + "/approve",
                map("externalTxHash", sameChainHash)));
        assertEquals(1, Concurrently.count(rs, 200), "the unique constraint must stop the second credit: " + rs.stream().map(ApiClient.Response::status).toList());
        assertMoney("5.00", balanceOf(a).add(balanceOf(b)));
    }

    @Test
    void approveRacingRejectHasExactlyOneWinnerAndTheBalanceMatchesIt() {
        ApiClient p = registerPlayer();
        fund(p, "10.00");
        p.post("/api/me/withdraw", map("amount", new BigDecimal("4.00"), "walletAddress", TON_WALLET), idem());
        String username = (String) p.get("/api/me").json().get("username");
        String tx = (String) admin().get("/api/admin/transactions/pending").jsonList().stream()
                .filter(t -> username.equals(t.get("username"))).findFirst().orElseThrow().get("txHash");

        List<ApiClient.Response> rs = Concurrently.runEach(THREADS, i -> i % 2 == 0
                ? admin().post("/api/admin/transactions/" + tx + "/approve", map())
                : admin().post("/api/admin/transactions/" + tx + "/reject", map()));
        assertEquals(1, Concurrently.count(rs, 200), rs.stream().map(ApiClient.Response::status).toList().toString());
        int winner = rs.indexOf(rs.stream().filter(r -> r.status() == 200).findFirst().orElseThrow());
        // approved withdrawal keeps the deduction (6.00); rejected one refunds it (10.00)
        assertMoney(winner % 2 == 0 ? "6.00" : "10.00", balanceOf(p));
    }

    @Test
    void parallelBuysNeverSpendMoreThanTheBalance() {
        Map<String, Object> cheapest = anonymous().get("/api/cases").jsonList().stream()
                .min((x, y) -> ApiClient.money(x.get("price")).compareTo(ApiClient.money(y.get("price")))).orElseThrow();
        String slug = (String) cheapest.get("slug");
        BigDecimal price = ApiClient.money(cheapest.get("price"));
        BigDecimal funds = price.multiply(BigDecimal.valueOf(3)).max(new BigDecimal("1.00"));
        int affordable = funds.divideToIntegralValue(price).intValue();

        ApiClient p = registerPlayer();
        fund(p, funds.toPlainString());
        List<ApiClient.Response> rs = Concurrently.run(THREADS, () ->
                p.post("/api/me/cases/buy", map("item", slug, "quantity", 1), idem()));

        assertEquals(affordable, Concurrently.count(rs, 200), rs.stream().map(ApiClient.Response::status).toList().toString());
        BigDecimal balance = balanceOf(p);
        assertTrue(balance.signum() >= 0, "balance went negative: " + balance);
        assertEquals(0, funds.subtract(price.multiply(BigDecimal.valueOf(affordable))).compareTo(balance));
        assertEquals(affordable, ((List<?>) p.get("/api/me/items").json().get("items")).size());
    }

    @Test
    void parallelOpensCannotOpenTheSameCaseTwice() {
        Map<String, Object> cheapest = anonymous().get("/api/cases").jsonList().stream()
                .min((x, y) -> ApiClient.money(x.get("price")).compareTo(ApiClient.money(y.get("price")))).orElseThrow();
        String slug = (String) cheapest.get("slug");
        BigDecimal price = ApiClient.money(cheapest.get("price"));
        ApiClient p = registerPlayer();
        fund(p, price.multiply(BigDecimal.valueOf(2)).max(new BigDecimal("1.00")).toPlainString());
        assertEquals(200, p.post("/api/me/cases/buy", map("item", slug, "quantity", 2), idem()).status());
        BigDecimal before = balanceOf(p);

        List<ApiClient.Response> rs = Concurrently.run(THREADS, () -> p.post("/api/me/cases/open", map("item", slug, "quantity", 1)));
        assertEquals(2, Concurrently.count(rs, 200), "only the two owned cases can be opened: " + rs.stream().map(ApiClient.Response::status).toList());
        BigDecimal wins = rs.stream().filter(r -> r.status() == 200)
                .map(r -> ApiClient.money(r.json().get("totalWonAmount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, before.add(wins).compareTo(balanceOf(p)), "credited exactly the sum of the two reported wins");
        assertTrue(((List<?>) p.get("/api/me/items").json().get("items")).isEmpty());
    }

    @Test
    void parallelRegistrationsOfOneUsernameYieldOneAccountAndNoServerErrors() {
        String name = uniqueUsername();
        List<ApiClient.Response> rs = Concurrently.run(THREADS, () ->
                anonymous().post("/api/auth/register", map("username", name, "password", PLAYER_PASSWORD)));
        assertEquals(1, Concurrently.count(rs, 201));
        assertEquals(THREADS - 1, Concurrently.count(rs, 409));
    }

    @Test
    void parallelPasswordChangesLeaveExactlyOneValidPassword() {
        String name = uniqueUsername();
        ApiClient p = registerPlayer(name);
        List<ApiClient.Response> rs = Concurrently.runEach(5, i -> p.post("/api/auth/password",
                map("currentPassword", PLAYER_PASSWORD, "newPassword", "new-password-number-" + i)));
        long ok = Concurrently.count(rs, 200);
        assertTrue(ok >= 1, rs.stream().map(ApiClient.Response::status).toList().toString());
        long usable = 0;
        for (int i = 0; i < 5; i++) {
            if (anonymous().post("/api/auth/login", map("username", name, "password", "new-password-number-" + i)).status() == 200) usable++;
        }
        assertEquals(1, usable, "the last committed change wins and only one password works");
        assertEquals(401, anonymous().post("/api/auth/login", map("username", name, "password", PLAYER_PASSWORD)).status());
    }
}
