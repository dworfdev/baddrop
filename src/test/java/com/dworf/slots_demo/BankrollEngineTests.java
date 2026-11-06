package com.dworf.slots_demo;

import com.dworf.slots_demo.dto.BankrollResponse;
import com.dworf.slots_demo.entity.ItemType;
import com.dworf.slots_demo.service.BankrollSplit;
import com.dworf.slots_demo.service.CaseOpenerEngine;
import com.dworf.slots_demo.service.CaseOpeningService;
import com.dworf.slots_demo.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Split-Inflow Dynamic Bankroll Engine & Ephemeral Floor Buffer Test Suite.
 * Systematized into 5 functional areas:
 * <ol>
 *     <li>Bankroll Singleton State & Exposure (DB Row Seeding, Profit Pool, House Profit, Policy Rate 33%)</li>
 *     <li>Turn-Zero Launch Safety (Zero debt risk from spin 0; wins strictly capped at C * 0.33 when pool is empty)</li>
 *     <li>Dynamic Pre-Roll Filtering (Pruning unaffordable items from the active catalogue before rolling)</li>
 *     <li>Conservation of Funds & Exact 3-Way Split (ΔHouse == 7% Cost, ΔPool + Payouts == 93% Cost across 3,000 spins)</li>
 *     <li>High-Concurrency Locking (Pessimistic write locking prevents race conditions and lost updates)</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BankrollEngineTests {

    @Autowired
    private CaseOpeningService caseOpeningService;

    @Autowired
    private CaseOpenerEngine caseOpenerEngine;

    @Autowired
    private UserService userService;

    // =========================================================================
    // 1. BANKROLL SINGLETON STATE & EXPOSURE
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1.1 Singleton bankroll row is seeded and exposes profit pool, house profit, and 33% policy rate")
    void testBankrollRowSeededAndExposed() {
        BankrollResponse bankroll = userService.getBankroll();
        assertNotNull(bankroll, "Bankroll response must not be null");
        assertNotNull(bankroll.profitPool(), "Profit pool must not be null");
        assertNotNull(bankroll.houseProfit(), "House profit must not be null");

        assertTrue(bankroll.profitPool().signum() >= 0, "Profit pool must never be negative");
        assertTrue(bankroll.houseProfit().signum() >= 0, "House profit must never be negative");
        assertEquals("33%", bankroll.floorBufferRate(), "Floor buffer rate must display active 33% policy");
    }

    // =========================================================================
    // 2. TURN-ZERO LAUNCH SAFETY (ZERO DEBT GUARANTEE)
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("2.1 Turn-Zero Launch Safety: When PrizePool = 0, MaxAllowedDrop = C * 0.33, preventing deficit")
    void testTurnZeroLaunchSafetyGuaranteesSolvency() {
        for (ItemType type : ItemType.values()) {
            BigDecimal cost = type.getPrice();
            BigDecimal floorBuffer = cost.multiply(BankrollSplit.FLOOR_RATE);
            BigDecimal emptyPool = BigDecimal.ZERO;
            BigDecimal maxAllowedDrop = emptyPool.add(floorBuffer);

            // Simulate 1,000 rolls with an empty treasury
            for (int i = 0; i < 1000; i++) {
                CaseOpenerEngine.Outcome outcome = caseOpenerEngine.drawOutcome(type, maxAllowedDrop);

                // Win amount must strictly fit within the ephemeral floor buffer
                assertTrue(outcome.winAmount().compareTo(floorBuffer) <= 0,
                        "At turn zero, drop " + outcome.winAmount() + " exceeded floorBuffer " + floorBuffer + " for " + type);

                // Must be classified as a floor/consolation drop
                assertTrue(BankrollSplit.isFloorValue(type, outcome.winAmount()),
                        "Drop must be a floor item during turn zero");
            }
        }
    }

    // =========================================================================
    // 3. DYNAMIC PRE-ROLL FILTERING
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("3.1 Pre-Roll Filtering: Items exceeding MaxAllowedDrop (PrizePool + FloorBuffer) are eliminated")
    void testDropsAreNeverDrawnAboveMaxAllowedDrop() {
        for (ItemType type : ItemType.values()) {
            // Test with an artificial low budget: 50% of crate price
            BigDecimal restrictedMax = type.getPrice().multiply(new BigDecimal("0.50"));

            for (int i = 0; i < 2000; i++) {
                CaseOpenerEngine.Outcome outcome = caseOpenerEngine.drawOutcome(type, restrictedMax);
                assertTrue(outcome.winAmount().compareTo(restrictedMax) <= 0,
                        "Drop " + outcome.winAmount() + " exceeded restricted maximum " + restrictedMax + " for " + type);
            }
        }
    }

    // =========================================================================
    // 4. CONSERVATION OF FUNDS & EXACT 3-WAY ACCOUNTING
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("4.1 Exact 3-Way Split Accounting over 3,000 spins: ΔHouse == 7% Cost, ΔPool + Payouts == 93% Cost")
    void testSequentialSpinsPreserveExactThreeWayAccounting() {
        BankrollResponse before = caseOpeningService.getBankroll();
        int spinsPerCase = 1000;

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        Map<ItemType, BigDecimal> paidByCase = new EnumMap<>(ItemType.class);
        Map<ItemType, BigDecimal> costByCase = new EnumMap<>(ItemType.class);

        // Spin each case: PEPE_IRON ($2), SHIBA_INU_GOLD ($5), DOGE_AMETHYST ($11)
        for (ItemType type : ItemType.values()) {
            for (int i = 0; i < spinsPerCase; i++) {
                CaseOpeningService.SpinResult spin = caseOpeningService.open(type);
                BigDecimal won = spin.outcome().winAmount();

                // Floor classification must strictly adhere to the 33% threshold
                assertEquals(won.compareTo(BankrollSplit.floorThreshold(type)) <= 0, spin.floorItem());

                // Invariant: Prize pool must never dip below 0
                BankrollResponse current = caseOpeningService.getBankroll();
                assertTrue(current.profitPool().signum() >= 0, "Prize pool went negative during gameplay");

                totalCost = totalCost.add(type.getPrice());
                totalPaid = totalPaid.add(won);
                paidByCase.merge(type, won, BigDecimal::add);
                costByCase.merge(type, type.getPrice(), BigDecimal::add);
            }
        }

        BankrollResponse after = caseOpeningService.getBankroll();
        assertExactAccountingIdentity(before, after, totalCost, totalPaid);

        paidByCase.forEach((type, paid) -> System.out.printf(
                "[BANKROLL-TEST] %-15s effective RTP over %d spins = %.2f%% (configured target %.2f%%)%n",
                type, spinsPerCase,
                paid.doubleValue() / costByCase.get(type).doubleValue() * 100.0,
                caseOpenerEngine.calculateStats(type).rtpPercent()));
        System.out.printf("[BANKROLL-TEST] profitPool %s -> %s | floorBufferRate %s | houseProfit %s -> %s%n",
                before.profitPool(), after.profitPool(),
                after.floorBufferRate(),
                before.houseProfit(), after.houseProfit());
    }

    // =========================================================================
    // 5. HIGH-CONCURRENCY PESSIMISTIC LOCKING
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("5.1 Concurrent Spins: 8 threads, 1,200 spins under pessimistic write lock maintain zero lost updates")
    void testConcurrentSpinsConsistencyUnderPessimisticLock() throws Exception {
        BankrollResponse before = caseOpeningService.getBankroll();
        int threads = 8;
        int spinsPerThread = 150;
        ItemType[] types = ItemType.values();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<BigDecimal[]>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final ItemType type = types[t % types.length];
            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                BigDecimal cost = BigDecimal.ZERO;
                BigDecimal paid = BigDecimal.ZERO;
                for (int i = 0; i < spinsPerThread; i++) {
                    paid = paid.add(caseOpeningService.open(type).outcome().winAmount());
                    cost = cost.add(type.getPrice());
                }
                return new BigDecimal[]{cost, paid};
            }));
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (Future<BigDecimal[]> future : futures) {
            BigDecimal[] result = future.get(120, TimeUnit.SECONDS);
            totalCost = totalCost.add(result[0]);
            totalPaid = totalPaid.add(result[1]);
        }
        executor.shutdownNow();

        BankrollResponse after = caseOpeningService.getBankroll();
        assertTrue(after.profitPool().signum() >= 0, "Prize pool must not be negative after concurrent load");
        assertEquals("33%", after.floorBufferRate());

        // Exact money conservation must hold down to the penny across all concurrent threads
        assertExactAccountingIdentity(before, after, totalCost, totalPaid);

        System.out.printf("[BANKROLL-TEST] Concurrent: %d spins, cost=%s, paid=%s, zero lost updates%n",
                threads * spinsPerThread, totalCost, totalPaid);
    }

    // =========================================================================
    // 6. ATOMIC BANKROLL RESET
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("6.1 Atomic Bankroll Reset: Resets Prize Pool and House Profit to 0.0000 under lock")
    void testAtomicBankrollResetClearsMetrics() {
        // Spin to accrue non-zero house profit
        caseOpeningService.open(ItemType.PEPE_IRON);

        BankrollResponse beforeReset = caseOpeningService.getBankroll();
        assertTrue(beforeReset.houseProfit().signum() > 0, "House profit should be > 0 after opening a case");

        BankrollResponse resetResult = caseOpeningService.resetBankroll();
        assertNotNull(resetResult);
        assertEquals(0, resetResult.profitPool().compareTo(BigDecimal.ZERO), "Prize pool must be reset to 0");
        assertEquals(0, resetResult.houseProfit().compareTo(BigDecimal.ZERO), "House profit must be reset to 0");

        BankrollResponse current = caseOpeningService.getBankroll();
        assertEquals(0, current.profitPool().compareTo(BigDecimal.ZERO), "Persisted prize pool must be 0");
        assertEquals(0, current.houseProfit().compareTo(BigDecimal.ZERO), "Persisted house profit must be 0");
    }

    /**
     * Mathematical Identity Guard:
     * 1. House profit delta == exactly 7% of total spin cost.
     * 2. (Prize pool delta + total user payouts) == exactly 93% of total spin cost.
     * 3. Total Money In == Total Money Out + Total Money Retained.
     */
    private void assertExactAccountingIdentity(
            BankrollResponse before, BankrollResponse after, BigDecimal totalCost, BigDecimal totalPaid
    ) {
        BigDecimal houseDelta = after.houseProfit().subtract(before.houseProfit());
        BigDecimal poolDelta = after.profitPool().subtract(before.profitPool());

        assertEquals(0, totalCost.multiply(BankrollSplit.HOUSE_RATE).compareTo(houseDelta),
                "House profit must increase by exactly 7% of total crate spend");

        assertEquals(0,
                totalCost.multiply(BankrollSplit.POOL_RATE.add(BankrollSplit.FLOOR_RATE))
                        .compareTo(poolDelta.add(totalPaid)),
                "Prize pool delta plus player payouts must equal exactly 93% of total crate spend");
    }
}
