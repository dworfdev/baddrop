package com.dworf.slots_demo;

import com.dworf.slots_demo.dto.BankrollResponse;
import com.dworf.slots_demo.entity.ItemType;
import com.dworf.slots_demo.service.BankrollSplit;
import com.dworf.slots_demo.service.CaseOpenerEngine;
import com.dworf.slots_demo.service.CaseOpeningService;
import com.dworf.slots_demo.service.UserService;
import org.junit.jupiter.api.Test;
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
 * Split-Inflow Dynamic Bankroll Engine with Ephemeral Floor Buffer tests.
 * Asserts on:
 * <ul>
 *     <li>Turn-zero launch safety (PrizePool = 0 guarantees solvency, caps drops at C * 0.33, snowballs pool)</li>
 *     <li>Pre-roll dynamic filtering (never drops items > PrizePool + FloorBuffer)</li>
 *     <li>Exact 3-way split accounting identity: House += 7% C, PrizePool + Payouts == 93% C</li>
 *     <li>Admin metrics exposure (Profit Pool, House Profit, Floor Buffer Rate = "33%")</li>
 *     <li>Pessimistic write locking & concurrency consistency under high load</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class BankrollEngineTests {

    @Autowired
    private CaseOpeningService caseOpeningService;

    @Autowired
    private CaseOpenerEngine caseOpenerEngine;

    @Autowired
    private UserService userService;

    @Test
    void bankrollRowIsSeededAndExposedToAdmin() {
        BankrollResponse snapshot = userService.getBankroll();
        assertNotNull(snapshot);
        assertTrue(snapshot.profitPool().signum() >= 0);
        assertTrue(snapshot.houseProfit().signum() >= 0);
        assertEquals("33%", snapshot.floorBufferRate());
    }

    @Test
    void turnZeroLaunchGuaranteesSolvencyAndSnowballsTreasury() {
        for (ItemType type : ItemType.values()) {
            BigDecimal cost = type.getPrice();
            BigDecimal floorBuffer = cost.multiply(BankrollSplit.FLOOR_RATE);
            BigDecimal currentPrizePool = BigDecimal.ZERO;
            BigDecimal maxAllowedDrop = currentPrizePool.add(floorBuffer);

            for (int i = 0; i < 1000; i++) {
                CaseOpenerEngine.Outcome outcome = caseOpenerEngine.drawOutcome(type, maxAllowedDrop);
                assertTrue(outcome.winAmount().compareTo(floorBuffer) <= 0,
                        "At turn zero (PrizePool = 0), drop " + outcome.winAmount() + " exceeded floorBuffer " + floorBuffer);
                assertTrue(BankrollSplit.isFloorValue(type, outcome.winAmount()));
            }
        }
    }

    @Test
    void sequentialSpinsKeepExactAccountingAndNeverGoNegative() {
        BankrollResponse before = caseOpeningService.getBankroll();
        int spinsPerCase = 1000;

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        Map<ItemType, BigDecimal> paidByCase = new EnumMap<>(ItemType.class);
        Map<ItemType, BigDecimal> costByCase = new EnumMap<>(ItemType.class);

        for (ItemType type : ItemType.values()) {
            for (int i = 0; i < spinsPerCase; i++) {
                CaseOpeningService.SpinResult spin = caseOpeningService.open(type);
                BigDecimal won = spin.outcome().winAmount();

                // Floor classification must follow the 33% rule
                assertEquals(won.compareTo(BankrollSplit.floorThreshold(type)) <= 0, spin.floorItem());

                BankrollResponse now = caseOpeningService.getBankroll();
                assertTrue(now.profitPool().signum() >= 0, "profit pool went negative");

                totalCost = totalCost.add(type.getPrice());
                totalPaid = totalPaid.add(won);
                paidByCase.merge(type, won, BigDecimal::add);
                costByCase.merge(type, type.getPrice(), BigDecimal::add);
            }
        }

        BankrollResponse after = caseOpeningService.getBankroll();
        assertAccountingIdentity(before, after, totalCost, totalPaid);

        paidByCase.forEach((type, paid) -> System.out.printf(
                "[BANKROLL-TEST] %-12s effective RTP over %d spins = %.2f%% (yaml target %.2f%%)%n",
                type, spinsPerCase,
                paid.doubleValue() / costByCase.get(type).doubleValue() * 100.0,
                caseOpenerEngine.calculateStats(type).rtpPercent()));
        System.out.printf("[BANKROLL-TEST] profitPool %s -> %s | floorBufferRate %s | houseProfit %s -> %s%n",
                before.profitPool(), after.profitPool(),
                after.floorBufferRate(),
                before.houseProfit(), after.houseProfit());
    }

    @Test
    void dropsAreNeverDrawnAboveMaxAllowedDrop() {
        for (ItemType type : ItemType.values()) {
            // Test with a tight pool budget
            BigDecimal maxDrop = type.getPrice().multiply(new BigDecimal("0.50"));
            for (int i = 0; i < 3000; i++) {
                CaseOpenerEngine.Outcome outcome = caseOpenerEngine.drawOutcome(type, maxDrop);
                assertTrue(outcome.winAmount().compareTo(maxDrop) <= 0,
                        type + " dropped " + outcome.winAmount() + " which exceeds maxAllowedDrop " + maxDrop);
            }
        }
    }

    @Test
    void concurrentSpinsAreSerializedAndStayConsistent() throws Exception {
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
        assertTrue(after.profitPool().signum() >= 0);
        assertEquals("33%", after.floorBufferRate());
        assertAccountingIdentity(before, after, totalCost, totalPaid);
        System.out.printf("[BANKROLL-TEST] concurrent: %d spins, cost=%s, paid=%s, no lost updates%n",
                threads * spinsPerThread, totalCost, totalPaid);
    }

    /**
     * Exact 3-way split conservation of money:
     * House margin grows by exactly 7% of total cost.
     * Remaining 93% of total cost either entered the shared prize pool or was paid out to users:
     * poolDelta + totalPaid == 93% of total cost.
     */
    private void assertAccountingIdentity(
            BankrollResponse before, BankrollResponse after, BigDecimal totalCost, BigDecimal totalPaid
    ) {
        BigDecimal houseDelta = after.houseProfit().subtract(before.houseProfit());
        BigDecimal poolDelta = after.profitPool().subtract(before.profitPool());

        assertEquals(0, totalCost.multiply(BankrollSplit.HOUSE_RATE).compareTo(houseDelta),
                "house profit must grow by exactly 7% of total cost");
        assertEquals(0,
                totalCost.multiply(BankrollSplit.POOL_RATE.add(BankrollSplit.FLOOR_RATE))
                        .compareTo(poolDelta.add(totalPaid)),
                "prize pool delta plus payouts must equal exactly 93% of total cost");
    }
}
