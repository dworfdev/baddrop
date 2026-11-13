package com.dworf.slots_demo.integration;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Admin: review queue, overview, audit trail, bankroll")
class AdminFlowTest extends IntegrationTestBase {

    @Test
    void pendingQueueContainsConfirmedDepositsOnly() {
        ApiClient p = registerPlayer();
        String username = (String) p.get("/api/me").json().get("username");
        String tx = (String) p.post("/api/me/deposit/start", map("amount", new BigDecimal("3.00")), idem()).json().get("txHash");

        assertTrue(admin().get("/api/admin/transactions/pending").jsonList().stream().noneMatch(t -> username.equals(t.get("username"))),
                "an unconfirmed (AWAITING_PAYMENT) deposit must not appear in the review queue");
        p.post("/api/me/deposit/confirm", map("txHash", tx, "receipt", freshReceipt()));
        assertTrue(admin().get("/api/admin/transactions/pending").jsonList().stream().anyMatch(t -> username.equals(t.get("username"))));
    }

    @Test
    void userOverviewShowsBalanceAndApprovedHistory() {
        ApiClient p = registerPlayer();
        String username = (String) p.get("/api/me").json().get("username");
        fund(p, "7.00");
        ApiClient.Response r = admin().get("/api/admin/users/" + username);
        assertEquals(200, r.status(), r.body());
        assertMoney("7.00", ApiClient.money(r.json().get("balance")));
        assertEquals(1, ((List<?>) r.json().get("transactionHistory")).size());
        assertEquals(404, admin().get("/api/admin/users/" + uniqueUsername()).status());
    }

    @Test
    void approvalsAreWrittenToTheAuditTrailWithTheActor() {
        ApiClient p = registerPlayer();
        ApiClient.Response approved = fund(p, "3.00");
        assertEquals(200, approved.status());
        List<Map<String, Object>> audit = admin().get("/api/admin/audit").jsonList();
        assertTrue(audit.stream().anyMatch(a -> "TX_APPROVED".equals(a.get("action")) && ADMIN_USER.equals(a.get("actor"))
                && approved.json().get("txHash").equals(a.get("target"))));
    }

    @Test
    void bankrollAndReconciliationEndpointsRespond() {
        ApiClient.Response b = admin().get("/api/admin/bankroll");
        assertEquals(200, b.status());
        assertEquals("33%", b.json().get("floorBufferRate"));
        ApiClient.Response rec = admin().get("/api/admin/reconciliation");
        assertEquals(200, rec.status(), rec.body());
        assertTrue(rec.json().containsKey("difference"));
    }

    @Test
    void bankrollResetIsDisabledByDefaultAndTheAttemptIsAudited() {
        ApiClient.Response r = admin().post("/api/admin/bankroll/reset", map("confirmation", "RESET BANKROLL AS ADMIN"));
        assertEquals(403, r.status(), r.body());
        assertTrue(admin().get("/api/admin/audit").jsonList().stream().anyMatch(a -> "BANKROLL_RESET_DENIED".equals(a.get("action"))));
    }

    @Test
    void unknownOrMalformedTransactionHashesAreHandledCleanly() {
        assertEquals(404, admin().post("/api/admin/transactions/" + "f".repeat(64) + "/reject", map()).status());
        assertEquals(400, admin().post("/api/admin/transactions/" + "f".repeat(65) + "/reject", map()).status());
    }

    @Test
    void adminCanLookAPlayerUpByTelegramId() {
        String name = uniqueUsername();
        registerPlayer(name);
        ApiClient.Response r = admin().get("/api/admin/users/" + telegramIdOf(name));
        assertEquals(200, r.status(), r.body());
        assertEquals(name, r.json().get("username"));
        assertEquals(telegramIdOf(name), ((Number) r.json().get("telegramId")).longValue());
    }
}
