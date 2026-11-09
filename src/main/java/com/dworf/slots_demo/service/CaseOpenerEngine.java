package com.dworf.slots_demo.service;

import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Dynamic Instant Win Case Opener Mathematical Engine.
 * Parses case multipliers & probability weights from the database {@code paytable_json},
 * validates PMF = 1.0, EV, RTP % (92-94%), House Edge %, and Variance (sigma^2),
 * and resolves outcomes via Cumulative Distribution Function (CDF) interval mapping
 * directly from a passed {@link List} of parsed paytable outcomes.
 */
@Component
public class CaseOpenerEngine {

    public record PaytableTier(
            BigDecimal multiplier,
            double probability,
            String label
    ) {
    }

    public record Outcome(
            BigDecimal multiplier,
            BigDecimal winAmount,
            double probability,
            String tierLabel
    ) {
    }

    public record CaseMathStats(
            String slug,
            BigDecimal caseCost,
            double totalProbability,
            BigDecimal expectedValue,
            double rtpPercent,
            double houseEdgePercent,
            double variance,
            List<Outcome> paytable
    ) {
    }

    private static final TypeReference<List<PaytableTier>> PAYTABLE_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random rng = new SecureRandom();

    /**
     * Parses the raw JSON string from {@code cases.paytable_json} into a list of {@link PaytableTier} entries.
     */
    public List<PaytableTier> parsePaytableJson(String paytableJson) {
        if (paytableJson == null || paytableJson.isBlank()) {
            throw new IllegalArgumentException("paytable_json must not be blank");
        }
        try {
            List<PaytableTier> tiers = objectMapper.readValue(paytableJson, PAYTABLE_LIST_TYPE);
            if (tiers == null || tiers.isEmpty()) {
                throw new IllegalArgumentException("Parsed paytable_json is empty");
            }
            return tiers;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid paytable_json format: " + e.getMessage(), e);
        }
    }

    /**
     * Converts parsed {@link PaytableTier} objects and the case price into concrete {@link Outcome} entries.
     */
    public List<Outcome> toOutcomes(BigDecimal caseCost, List<PaytableTier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("Paytable tiers must not be empty");
        }
        BigDecimal floorMax = BankrollSplit.floorThreshold(caseCost);
        return tiers.stream()
                .map(t -> {
                    BigDecimal rawWin = caseCost.multiply(t.multiplier());
                    BigDecimal roundedWin = rawWin.setScale(2, RoundingMode.HALF_UP);
                    if (t.multiplier().compareTo(BankrollSplit.FLOOR_RATE) <= 0
                            && roundedWin.compareTo(floorMax) > 0) {
                        roundedWin = rawWin.setScale(2, RoundingMode.DOWN);
                    }
                    return new Outcome(
                            t.multiplier(),
                            roundedWin,
                            t.probability(),
                            t.label()
                    );
                })
                .toList();
    }

    /**
     * Parses {@code paytable_json} and builds the concrete {@link Outcome} list for the given case cost.
     */
    public List<Outcome> parseOutcomes(BigDecimal caseCost, String paytableJson) {
        return toOutcomes(caseCost, parsePaytableJson(paytableJson));
    }

    /**
     * Verifies the mathematical integrity (PMF = 1.0 and presence of at least one floor item with multiplier <= 0.33) of a paytable.
     */
    public boolean validateMathematics(String slug, BigDecimal caseCost, List<Outcome> paytable) {
        CaseMathStats stats = calculateStats(slug, caseCost, paytable);
        if (Math.abs(stats.totalProbability() - 1.0) > 0.0001) {
            System.err.println("CRITICAL ERROR: Sum of probabilities must equal 1.0 for " + slug);
            return false;
        }
        boolean hasFloorItem = paytable.stream()
                .anyMatch(o -> o.probability() > 0.0 && o.multiplier().compareTo(BankrollSplit.FLOOR_RATE) <= 0);
        if (!hasFloorItem) {
            System.err.println("CRITICAL ERROR: Case " + slug + " lacks a floor item (multiplier <= "
                    + BankrollSplit.FLOOR_RATE.toPlainString() + ")");
            return false;
        }
        return true;
    }

    /**
     * Computes PMF sum, Expected Value (EV), Return to Player (RTP %), House Edge %, and Variance (sigma^2)
     * directly from a passed {@link List} of paytable outcomes.
     */
    public CaseMathStats calculateStats(String slug, BigDecimal caseCost, List<Outcome> paytable) {
        if (paytable == null || paytable.isEmpty()) {
            throw new IllegalArgumentException("Paytable must not be empty for case: " + slug);
        }

        double totalProbability = 0.0;
        double expectedValue = 0.0;

        for (Outcome outcome : paytable) {
            totalProbability += outcome.probability();
            expectedValue += outcome.winAmount().doubleValue() * outcome.probability();
        }

        double cost = caseCost.doubleValue();
        double rtp = (expectedValue / cost) * 100.0;
        double houseEdge = 100.0 - rtp;

        // Variance: sigma^2 = sum(P_i * (X_i - EV)^2)
        double variance = 0.0;
        for (Outcome outcome : paytable) {
            double diff = outcome.winAmount().doubleValue() - expectedValue;
            variance += outcome.probability() * (diff * diff);
        }

        return new CaseMathStats(
                slug,
                caseCost,
                roundDouble(totalProbability, 4),
                BigDecimal.valueOf(expectedValue).setScale(3, RoundingMode.HALF_UP),
                roundDouble(rtp, 2),
                roundDouble(houseEdge, 2),
                roundDouble(variance, 4),
                paytable
        );
    }

    /**
     * Resolves a drop for the Split-Inflow Dynamic Bankroll Engine with Ephemeral Floor Buffer
     * directly from a passed {@link List} of paytable outcomes.
     * Dynamic Pre-Roll Filtering:
     * ActivePool = { item in paytable | Value(item) <= MaxAllowedDrop }
     * where MaxAllowedDrop = PrizePool_current + FloorBuffer.
     */
    public Outcome drawOutcome(List<Outcome> paytable, BigDecimal maxAllowedDrop) {
        if (paytable == null || paytable.isEmpty()) {
            throw new IllegalArgumentException("Paytable must not be null or empty");
        }

        List<Outcome> activePool = new ArrayList<>(paytable.size());
        double totalWeight = 0.0;
        for (Outcome outcome : paytable) {
            boolean eligible = outcome.winAmount().compareTo(maxAllowedDrop) <= 0;
            if (eligible && outcome.probability() > 0.0) {
                activePool.add(outcome);
                totalWeight += outcome.probability();
            }
        }
        if (activePool.isEmpty()) {
            throw new IllegalStateException("No eligible drop in paytable with max allowed drop " + maxAllowedDrop);
        }

        double roll = rng.nextDouble() * totalWeight; // uniform in [0, W_total)
        double cumulative = 0.0;
        for (Outcome outcome : activePool) {
            cumulative += outcome.probability();
            if (roll < cumulative) {
                return outcome;
            }
        }
        return activePool.get(activePool.size() - 1);
    }

    public boolean isMaxWin(List<Outcome> paytable, BigDecimal winAmount) {
        if (paytable == null || paytable.isEmpty() || winAmount == null) {
            return false;
        }
        BigDecimal maxWin = paytable.stream()
                .map(Outcome::winAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        return winAmount.compareTo(maxWin) >= 0;
    }

    private static double roundDouble(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
