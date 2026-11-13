package com.dworf.slots_demo.unit;

import com.dworf.slots_demo.service.BankrollSplit;
import com.dworf.slots_demo.service.CaseOpenerEngine;
import com.dworf.slots_demo.service.CaseOpenerEngine.PaytableTier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure engine maths on a synthetic paytable: no Spring, no database. */
@Tag("unit")
class CaseOpenerEngineTest {
    private final CaseOpenerEngine engine = new CaseOpenerEngine();
    private static final BigDecimal COST = new BigDecimal("10.00");

    private static List<PaytableTier> tiers() {
        return List.of(
                new PaytableTier(new BigDecimal("0.10"), 0.50, "Dust"),
                new PaytableTier(new BigDecimal("0.30"), 0.30, "Common"),
                new PaytableTier(new BigDecimal("1.00"), 0.15, "Rare"),
                new PaytableTier(new BigDecimal("5.00"), 0.05, "Jackpot"));
    }

    private List<CaseOpenerEngine.Outcome> outcomes() {
        return engine.toOutcomes(COST, tiers());
    }

    @Test
    void outcomesAreMultiplierTimesCostRoundedToCents() {
        var o = outcomes();
        assertEquals(new BigDecimal("1.00"), o.get(0).winAmount());
        assertEquals(new BigDecimal("3.00"), o.get(1).winAmount());
        assertEquals(new BigDecimal("10.00"), o.get(2).winAmount());
        assertEquals(new BigDecimal("50.00"), o.get(3).winAmount());
    }

    @Test
    void floorTiersNeverRoundAboveTheFloorThreshold() {
        // price 0.07 * 0.33 = 0.0231 -> HALF_UP would give 0.02 which is fine, but 0.05 * 0.33 = 0.0165 -> 0.02 > 0.0165
        BigDecimal price = new BigDecimal("0.05");
        var o = engine.toOutcomes(price, List.of(new PaytableTier(new BigDecimal("0.33"), 1.0, "floor")));
        assertTrue(o.get(0).winAmount().compareTo(BankrollSplit.floorThreshold(price)) <= 0,
                "a floor drop must never exceed the 33% buffer, otherwise it can be paid from a pool that is empty");
    }

    @Test
    void statsReportExpectedValueAndRtp() {
        var stats = engine.calculateStats("t", COST, outcomes());
        // EV = 0.5*1 + 0.3*3 + 0.15*10 + 0.05*50 = 0.5 + 0.9 + 1.5 + 2.5 = 5.4  -> RTP 54%
        assertEquals(0, new BigDecimal("5.400").compareTo(stats.expectedValue()));
        assertEquals(54.0, stats.rtpPercent(), 0.001);
        assertEquals(1.0, stats.totalProbability(), 0.0001);
        assertEquals(46.0, stats.houseEdgePercent(), 0.001);
    }

    @Test
    void validatesProbabilitySumAndFloorItem() {
        assertTrue(engine.validateMathematics("ok", COST, outcomes()));
        var badSum = engine.toOutcomes(COST, List.of(
                new PaytableTier(new BigDecimal("0.10"), 0.5, "a"),
                new PaytableTier(new BigDecimal("1.00"), 0.4, "b")));
        assertFalse(engine.validateMathematics("sum", COST, badSum));
        var noFloor = engine.toOutcomes(COST, List.of(
                new PaytableTier(new BigDecimal("0.50"), 0.5, "a"),
                new PaytableTier(new BigDecimal("2.00"), 0.5, "b")));
        assertFalse(engine.validateMathematics("nofloor", COST, noFloor));
    }

    @Test
    void rtpCapMatchesTheSevenPercentHouseCut() {
        assertTrue(CaseOpenerEngine.rtpWithinHouseCap(93.0));
        assertTrue(CaseOpenerEngine.rtpWithinHouseCap(54.0));
        assertFalse(CaseOpenerEngine.rtpWithinHouseCap(93.01));
        assertFalse(CaseOpenerEngine.rtpWithinHouseCap(100.0));
    }

    @Test
    void drawNeverExceedsMaxAllowedDrop() {
        var o = outcomes();
        BigDecimal cap = new BigDecimal("3.00");
        for (int i = 0; i < 5000; i++) {
            assertTrue(engine.drawOutcome(o, cap).winAmount().compareTo(cap) <= 0);
        }
    }

    @Test
    void drawWithEmptyPoolOnlyYieldsFloorItems() {
        var o = outcomes();
        BigDecimal floor = BankrollSplit.floorThreshold(COST);
        for (int i = 0; i < 5000; i++) {
            assertTrue(engine.drawOutcome(o, floor).winAmount().compareTo(floor) <= 0);
        }
    }

    @Test
    void drawFailsLoudlyWhenNothingIsAffordable() {
        assertThrows(IllegalStateException.class, () -> engine.drawOutcome(outcomes(), new BigDecimal("0.50")));
    }

    @Test
    void drawIsRoughlyProportionalToWeights() {
        var o = outcomes();
        int n = 100_000;
        int dust = 0;
        for (int i = 0; i < n; i++) {
            if (engine.drawOutcome(o, new BigDecimal("1000")).tierLabel().equals("Dust")) dust++;
        }
        assertEquals(0.50, dust / (double) n, 0.01);
    }

    @Test
    void renormalisesWeightsAfterFiltering() {
        // with cap 3.00 only Dust(0.5) and Common(0.3) remain -> Dust should be 0.5/0.8 = 62.5%
        var o = outcomes();
        int n = 100_000;
        int dust = 0;
        for (int i = 0; i < n; i++) {
            if (engine.drawOutcome(o, new BigDecimal("3.00")).tierLabel().equals("Dust")) dust++;
        }
        assertEquals(0.625, dust / (double) n, 0.01);
    }

    @Test
    void emptyPaytableIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> engine.toOutcomes(COST, List.of()));
        assertThrows(IllegalArgumentException.class, () -> engine.drawOutcome(List.of(), COST));
    }

    @Test
    void splitRatesSumToOne() {
        assertEquals(0, BigDecimal.ONE.compareTo(
                BankrollSplit.HOUSE_RATE.add(BankrollSplit.FLOOR_RATE).add(BankrollSplit.POOL_RATE)));
    }
}
