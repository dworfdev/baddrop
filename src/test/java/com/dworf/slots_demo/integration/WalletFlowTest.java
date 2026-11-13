package com.dworf.slots_demo.integration;

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

@Tag("integration")
@DisplayName("Wallet: deposit session, withdrawal, admin approval")
class WalletFlowTest extends IntegrationTestBase {

    @Test
    void depositHappyPathCreditsBalanceOnlyAfterAdminApproval() {
        ApiClient p = registerPlayer();
        ApiClient.Response start = p.post("/api/me/deposit/start", map("amount", new BigDecimal("12.50")), idem());
        assertEquals(200, start.status(), start.body());
        assertEquals(true, start.json().get("active"));
        String txHash = (String) start.json().get("txHash");
        assertMoney("0.00", balanceOf(p));

        assertEquals(200, p.post("/api/me/deposit/confirm", map("txHash", txHash)).status());
        assertMoney("0.00", balanceOf(p));

        ApiClient.Response approve = admin().post("/api/admin/transactions/" + txHash + "/approve", map("externalTxHash", "chain" + freshKey().replace("-", "")));
        assertEquals(200, approve.status(), approve.body());
        assertMoney("12.50", balanceOf(p));
    }

    @Test
    void depositMinimumAndAmountFormatAreEnforced() {
        ApiClient p = registerPlayer();
        assertEquals(400, p.post("/api/me/deposit/start", map("amount", new BigDecimal("0.99")), idem()).status());
        assertEquals(400, p.post("/api/me/deposit/start", map("amount", new BigDecimal("-5")), idem()).status());
        assertEquals(400, p.post("/api/me/deposit/start", map("amount", new BigDecimal("1.001")), idem()).status());
        assertEquals(400, p.post("/api/me/deposit/start", map("amount", new BigDecimal("999999999")), idem()).status());
        assertEquals(400, p.post("/api/me/deposit/start", map(), idem()).status());
    }

    @Test
    void startingAnotherDepositExpiresThePreviousSession() {
        ApiClient p = registerPlayer();
        String first = (String) p.post("/api/me/deposit/start", map("amount", new BigDecimal("2.00")), idem()).json().get("txHash");
        String second = (String) p.post("/api/me/deposit/start", map("amount", new BigDecimal("3.00")), idem()).json().get("txHash");
        assertTrue(!first.equals(second));
        assertEquals(409, p.post("/api/me/deposit/confirm", map("txHash", first)).status(), "the superseded session must not be confirmable");
        assertEquals(200, p.post("/api/me/deposit/confirm", map("txHash", second)).status());
    }

    @Test
    void confirmingTwiceIsRejected() {
        ApiClient p = registerPlayer();
        String tx = (String) p.post("/api/me/deposit/start", map("amount", new BigDecimal("2.00")), idem()).json().get("txHash");
        assertEquals(200, p.post("/api/me/deposit/confirm", map("txHash", tx)).status());
        assertEquals(409, p.post("/api/me/deposit/confirm", map("txHash", tx)).status());
    }

    @Test
    void confirmRequiresTheCorrectHash() {
        ApiClient p = registerPlayer();
        p.post("/api/me/deposit/start", map("amount", new BigDecimal("2.00")), idem());
        assertEquals(400, p.post("/api/me/deposit/confirm", map()).status(), "txHash is mandatory");
        assertEquals(409, p.post("/api/me/deposit/confirm", map("txHash", "0".repeat(64))).status());
    }

    @Test
    void activeDepositEndpointReportsSessionState() {
        ApiClient p = registerPlayer();
        assertEquals(false, p.get("/api/me/deposit/active").json().get("active"));
        p.post("/api/me/deposit/start", map("amount", new BigDecimal("4.00")), idem());
        ApiClient.Response active = p.get("/api/me/deposit/active");
        assertEquals(true, active.json().get("active"));
        assertMoney("4.00", ApiClient.money(active.json().get("amount")));
    }

    @Test
    void depositApprovalNeedsAnOnChainHashAndItCannotBeReused() {
        ApiClient p1 = registerPlayer();
        String tx1 = (String) p1.post("/api/me/deposit/start", map("amount", new BigDecimal("5.00")), idem()).json().get("txHash");
        p1.post("/api/me/deposit/confirm", map("txHash", tx1));

        assertEquals(400, admin().post("/api/admin/transactions/" + tx1 + "/approve", map()).status(), "approval without an on-chain hash must fail");
        assertEquals(400, admin().post("/api/admin/transactions/" + tx1 + "/approve", map("externalTxHash", "short")).status());
        assertMoney("0.00", balanceOf(p1));

        String chain = "chain" + freshKey().replace("-", "");
        assertEquals(200, admin().post("/api/admin/transactions/" + tx1 + "/approve", map("externalTxHash", chain)).status());

        ApiClient p2 = registerPlayer();
        String tx2 = (String) p2.post("/api/me/deposit/start", map("amount", new BigDecimal("5.00")), idem()).json().get("txHash");
        p2.post("/api/me/deposit/confirm", map("txHash", tx2));
        ApiClient.Response reuse = admin().post("/api/admin/transactions/" + tx2 + "/approve", map("externalTxHash", chain));
        assertEquals(409, reuse.status(), "one on-chain transaction must never credit two deposits");
        assertMoney("0.00", balanceOf(p2));
    }

    @Test
    void rejectedDepositCreditsNothing() {
        ApiClient p = registerPlayer();
        String tx = (String) p.post("/api/me/deposit/start", map("amount", new BigDecimal("5.00")), idem()).json().get("txHash");
        p.post("/api/me/deposit/confirm", map("txHash", tx));
        assertEquals(200, admin().post("/api/admin/transactions/" + tx + "/reject", map()).status());
        assertMoney("0.00", balanceOf(p));
        assertEquals(409, admin().post("/api/admin/transactions/" + tx + "/approve", map("externalTxHash", "chain" + freshKey().replace("-", ""))).status(),
                "a rejected transaction cannot be approved afterwards");
    }

    @Test
    void withdrawalRequiresAnApprovedDepositFirst() {
        ApiClient p = registerPlayer();
        ApiClient.Response r = p.post("/api/me/withdraw", map("amount", new BigDecimal("5.00"), "walletAddress", TON_WALLET), idem());
        assertEquals(400, r.status(), r.body());
    }

    @Test
    void withdrawalDeductsImmediatelyAndRejectRefunds() {
        ApiClient p = registerPlayer();
        fund(p, "10.00");
        ApiClient.Response w = p.post("/api/me/withdraw", map("amount", new BigDecimal("4.00"), "walletAddress", TON_WALLET), idem());
        assertEquals(200, w.status(), w.body());
        assertMoney("6.00", balanceOf(p));

        Map<String, Object> pending = admin().get("/api/admin/transactions/pending").jsonList().stream()
                .filter(t -> "WITHDRAW".equals(t.get("type")) && p.get("/api/me").json().get("username").equals(t.get("username")))
                .findFirst().orElseThrow();
        assertEquals(200, admin().post("/api/admin/transactions/" + pending.get("txHash") + "/reject", map()).status());
        assertMoney("10.00", balanceOf(p));
    }

    @Test
    void withdrawalApprovalKeepsTheDeduction() {
        ApiClient p = registerPlayer();
        fund(p, "10.00");
        p.post("/api/me/withdraw", map("amount", new BigDecimal("4.00"), "walletAddress", TON_WALLET), idem());
        String username = (String) p.get("/api/me").json().get("username");
        Map<String, Object> pending = admin().get("/api/admin/transactions/pending").jsonList().stream()
                .filter(t -> username.equals(t.get("username"))).findFirst().orElseThrow();
        assertEquals(200, admin().post("/api/admin/transactions/" + pending.get("txHash") + "/approve", map()).status());
        assertMoney("6.00", balanceOf(p));
    }

    @Test
    void withdrawalValidatesAmountBalanceAndAddress() {
        ApiClient p = registerPlayer();
        fund(p, "10.00");
        assertEquals(400, p.post("/api/me/withdraw", map("amount", new BigDecimal("2.49"), "walletAddress", TON_WALLET), idem()).status(), "below minimum");
        assertEquals(400, p.post("/api/me/withdraw", map("amount", new BigDecimal("10.01"), "walletAddress", TON_WALLET), idem()).status(), "more than balance");
        assertEquals(400, p.post("/api/me/withdraw", map("amount", new BigDecimal("5.00"), "walletAddress", "not-a-ton-address"), idem()).status());
        assertEquals(400, p.post("/api/me/withdraw", map("amount", new BigDecimal("5.00"), "walletAddress", "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"), idem()).status());
        assertEquals(400, p.post("/api/me/withdraw", map("amount", new BigDecimal("5.00")), idem()).status());
        assertMoney("10.00", balanceOf(p));
    }

    @Test
    void onlyOnePendingTransactionPerUser() {
        ApiClient p = registerPlayer();
        fund(p, "10.00");
        assertEquals(200, p.post("/api/me/withdraw", map("amount", new BigDecimal("3.00"), "walletAddress", TON_WALLET), idem()).status());
        assertEquals(409, p.post("/api/me/withdraw", map("amount", new BigDecimal("3.00"), "walletAddress", TON_WALLET), idem()).status());
        assertEquals(409, p.post("/api/me/deposit/start", map("amount", new BigDecimal("3.00")), idem()).status());
    }

    @Test
    void moneyEndpointsRequireAnIdempotencyKey() {
        ApiClient p = registerPlayer();
        fund(p, "10.00");
        assertEquals(400, p.post("/api/me/deposit/start", map("amount", new BigDecimal("3.00"))).status());
        assertEquals(400, p.post("/api/me/withdraw", map("amount", new BigDecimal("3.00"), "walletAddress", TON_WALLET)).status());
        assertEquals(400, p.post("/api/me/deposit/start", map("amount", new BigDecimal("3.00")), Map.of("Idempotency-Key", "x")).status(), "malformed key");
    }

    @Test
    void replayingTheSameIdempotencyKeyIsRejectedAndNeverRepeatsTheEffect() {
        ApiClient p = registerPlayer();
        fund(p, "10.00");
        Map<String, String> key = idem();
        assertEquals(200, p.post("/api/me/withdraw", map("amount", new BigDecimal("3.00"), "walletAddress", TON_WALLET), key).status());
        ApiClient.Response replay = p.post("/api/me/withdraw", map("amount", new BigDecimal("3.00"), "walletAddress", TON_WALLET), key);
        assertEquals(409, replay.status());
        assertMoney("7.00", balanceOf(p));
    }

    @Test
    void aFailedOperationDoesNotBurnItsIdempotencyKey() {
        ApiClient p = registerPlayer();
        fund(p, "10.00");
        Map<String, String> key = idem();
        assertEquals(400, p.post("/api/me/withdraw", map("amount", new BigDecimal("999.00"), "walletAddress", TON_WALLET), key).status());
        assertEquals(200, p.post("/api/me/withdraw", map("amount", new BigDecimal("3.00"), "walletAddress", TON_WALLET), key).status(),
                "the claim must roll back together with the failed money operation");
    }
}
