package com.dworf.slots_demo.bankroll;

import com.dworf.slots_demo.dto.BankrollResponse;
import com.dworf.slots_demo.entity.CaseEntity;
import com.dworf.slots_demo.service.BankrollSplit;
import com.dworf.slots_demo.service.CaseOpenerEngine;
import com.dworf.slots_demo.service.CaseOpeningService;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Split-Inflow Dynamic Bankroll Engine against the seeded database catalogue.
 * Ported from the old BankrollEngineTests (areas 1-5); area 6 (reset) now lives in BankrollResetTest.
 * Spins go straight through CaseOpeningService.open(), i.e. without player money, so these tests only
 * assert deltas and never absolute bankroll values.
 */
@Tag("bankroll")
@DisplayName("Bankroll engine (database-backed catalogue)")
class BankrollEngineIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CaseOpeningService caseOpeningService;

    @Autowired
    private CaseOpenerEngine caseOpenerEngine;

    @Test
    @DisplayName("1. Singleton bankroll row exists and exposes the 33% policy rate")
    void bankrollRowIsSeededAndExposed() {
        BankrollResponse b = caseOpeningService.getBankroll();
        assertNotNull(b.profitPool());
        assertNotNull(b.houseProfit());
        assertTrue(b.profitPool().signum() >= 0, "prize pool must never be negative");
        assertTrue(b.houseProfit().signum() >= 0, "house profit must never be negative");
        assertEquals("33%", b.floorBufferRate());
    }

    @Test
    @DisplayName("2. Turn-zero safety: with an empty pool every drop fits the 33% floor buffer")
    void turnZeroOnlyYieldsFloorDrops() {
        List<CaseEntity> cases = caseOpeningService.getAllActiveCaseEntities();
        assertFalse(cases.isEmpty(), "active cases must be seeded");
        for (CaseEntity c : cases) {
            BigDecimal floorBuffer = c.getPrice().multiply(BankrollSplit.FLOOR_RATE);
            var paytable = caseOpenerEngine.parseOutcomes(c.getPrice(), c.getPaytableJson());
            for (int i = 0; i < 1000; i++) {
                var o = caseOpenerEngine.drawOutcome(paytable, floorBuffer);
                assertTrue(o.winAmount().compareTo(floorBuffer) <= 0, c.getSlug() + ": " + o.winAmount() + " > " + floorBuffer);
                assertTrue(BankrollSplit.isFloorValue(c.getPrice(), o.winAmount()));
            }
        }
    }

    @Test
    @DisplayName("3. Pre-roll filter never draws above the allowed maximum")
    void dropsNeverExceedMaxAllowedDrop() {
        for (CaseEntity c : caseOpeningService.getAllActiveCaseEntities()) {
            BigDecimal max = c.getPrice().multiply(new BigDecimal("0.50"));
            var paytable = caseOpenerEngine.parseOutcomes(c.getPrice(), c.getPaytableJson());
            for (int i = 0; i < 2000; i++) {
                assertTrue(caseOpenerEngine.drawOutcome(paytable, max).winAmount().compareTo(max) <= 0);
            }
        }
    }

    @Test
    @DisplayName("3b. Seeded catalogue passes the mathematical validation and reports its RTP")
    void seededCataloguePassesValidation() {
        for (CaseEntity c : caseOpeningService.getAllActiveCaseEntities()) {
            var paytable = caseOpenerEngine.parseOutcomes(c.getPrice(), c.getPaytableJson());
            assertTrue(caseOpenerEngine.validateMathematics(c.getSlug(), c.getPrice(), paytable), c.getSlug());
            var stats = caseOpenerEngine.calculateStats(c.getSlug(), c.getPrice(), paytable);
            System.out.printf("[BANKROLL] %-18s RTP %.2f%% (cap %.0f%%)%n", c.getSlug(), stats.rtpPercent(), CaseOpenerEngine.MAX_RTP_PERCENT);
            assertTrue(CaseOpenerEngine.rtpWithinHouseCap(stats.rtpPercent()),
                    c.getSlug() + " has RTP " + stats.rtpPercent() + "% - above the 93% a 7% house cut can fund (H12)");
        }
    }

    @Test
    @DisplayName("4. Exact 3-way accounting over 1,000 spins per case")
    void sequentialSpinsPreserveExactAccounting() {
        BankrollResponse before = caseOpeningService.getBankroll();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        Map<String, BigDecimal> paid = new LinkedHashMap<>();
        Map<String, BigDecimal> cost = new LinkedHashMap<>();

        for (CaseEntity c : caseOpeningService.getAllActiveCaseEntities()) {
            for (int i = 0; i < 1000; i++) {
                var spin = caseOpeningService.open(c.getSlug());
                BigDecimal won = spin.outcome().winAmount();
                assertEquals(won.compareTo(BankrollSplit.floorThreshold(c.getPrice())) <= 0, spin.floorItem());
                assertTrue(caseOpeningService.getBankroll().profitPool().signum() >= 0, "prize pool went negative");
                totalCost = totalCost.add(c.getPrice());
                totalPaid = totalPaid.add(won);
                paid.merge(c.getSlug(), won, BigDecimal::add);
                cost.merge(c.getSlug(), c.getPrice(), BigDecimal::add);
            }
        }
        assertAccountingIdentity(before, caseOpeningService.getBankroll(), totalCost, totalPaid);
        paid.forEach((slug, p) -> System.out.printf("[BANKROLL] %-18s effective RTP %.2f%%%n", slug, p.doubleValue() / cost.get(slug).doubleValue() * 100));
    }

    @Test
    @DisplayName("5. Concurrent spins: 8 threads x 150 spins, zero lost updates")
    void concurrentSpinsLoseNothing() throws Exception {
        BankrollResponse before = caseOpeningService.getBankroll();
        int threads = 8;
        int spinsPerThread = 150;
        List<CaseEntity> cases = caseOpeningService.getAllActiveCaseEntities();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<BigDecimal[]>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final CaseEntity c = cases.get(t % cases.size());
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                BigDecimal cost = BigDecimal.ZERO;
                BigDecimal paid = BigDecimal.ZERO;
                for (int i = 0; i < spinsPerThread; i++) {
                    paid = paid.add(caseOpeningService.open(c.getSlug()).outcome().winAmount());
                    cost = cost.add(c.getPrice());
                }
                return new BigDecimal[]{cost, paid};
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (Future<BigDecimal[]> f : futures) {
            BigDecimal[] r = f.get(180, TimeUnit.SECONDS);
            totalCost = totalCost.add(r[0]);
            totalPaid = totalPaid.add(r[1]);
        }
        pool.shutdownNow();

        BankrollResponse after = caseOpeningService.getBankroll();
        assertTrue(after.profitPool().signum() >= 0);
        assertAccountingIdentity(before, after, totalCost, totalPaid);
    }

    /** house delta == 7% of spend; (pool delta + payouts) == 93% of spend - exact to the cent. */
    private static void assertAccountingIdentity(BankrollResponse before, BankrollResponse after, BigDecimal cost, BigDecimal paid) {
        BigDecimal houseDelta = after.houseProfit().subtract(before.houseProfit());
        BigDecimal poolDelta = after.profitPool().subtract(before.profitPool());
        assertEquals(0, cost.multiply(BankrollSplit.HOUSE_RATE).compareTo(houseDelta), "house must gain exactly 7% of spend");
        assertEquals(0, cost.multiply(BankrollSplit.POOL_RATE.add(BankrollSplit.FLOOR_RATE)).compareTo(poolDelta.add(paid)),
                "pool delta + payouts must equal exactly 93% of spend");
    }
}
