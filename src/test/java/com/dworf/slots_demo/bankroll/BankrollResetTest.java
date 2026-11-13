package com.dworf.slots_demo.bankroll;

import com.dworf.slots_demo.dto.BankrollResponse;
import com.dworf.slots_demo.service.CaseOpeningService;
import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Own context with the reset switch ON; the context is discarded afterwards because reset breaks the books by design. */
@Tag("bankroll")
@DisplayName("Bankroll reset (guarded, audited)")
@TestPropertySource(properties = "slots.admin.allow-bankroll-reset=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BankrollResetTest extends IntegrationTestBase {

    @Autowired
    private CaseOpeningService caseOpeningService;

    @Test
    void serviceResetZeroesPoolAndHouseProfitAtomically() {
        caseOpeningService.open(caseOpeningService.getAllActiveCaseEntities().get(0).getSlug());
        assertTrue(caseOpeningService.getBankroll().houseProfit().signum() > 0);
        BankrollResponse r = caseOpeningService.resetBankroll();
        assertNotNull(r);
        assertEquals(0, r.profitPool().compareTo(BigDecimal.ZERO));
        assertEquals(0, r.houseProfit().compareTo(BigDecimal.ZERO));
        assertEquals(0, caseOpeningService.getBankroll().houseProfit().compareTo(BigDecimal.ZERO));
    }

    @Test
    void endpointNeedsTheConfirmationPhraseAndIsAudited() {
        ApiClient a = admin();
        assertEquals(400, a.post("/api/admin/bankroll/reset", map("confirmation", "yes")).status());
        assertEquals(400, a.post("/api/admin/bankroll/reset", map()).status());
        assertEquals(200, a.post("/api/admin/bankroll/reset", map("confirmation", "RESET BANKROLL AS ADMIN")).status());
        assertTrue(a.get("/api/admin/audit").jsonList().stream().anyMatch(x -> "BANKROLL_RESET".equals(x.get("action"))));
    }

    @Test
    void playersCanNeverCallIt() {
        assertEquals(403, registerPlayer().post("/api/admin/bankroll/reset", map("confirmation", "RESET BANKROLL AS ADMIN")).status());
        assertEquals(401, anonymous().post("/api/admin/bankroll/reset", map("confirmation", "RESET BANKROLL AS ADMIN")).status());
    }
}
