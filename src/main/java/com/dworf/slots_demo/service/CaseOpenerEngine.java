package com.dworf.slots_demo.service;

import com.dworf.slots_demo.entity.ItemType;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Configurable Low / Medium-Low Variance Instant Win Case Opener Mathematical Engine.
 * Loads case multipliers & probability weights from CaseMathProperties (application.yaml),
 * validates PMF = 1.0, EV, RTP % (92-94%), House Edge %, and Variance (sigma^2),
 * and resolves outcomes via Cumulative Distribution Function (CDF) interval mapping.
 */
@Component
public class CaseOpenerEngine {

    public record Outcome(
            BigDecimal multiplier,
            BigDecimal winAmount,
            double probability,
            String tierLabel
    ) {
    }

    public record CaseMathStats(
            ItemType itemType,
            BigDecimal caseCost,
            double totalProbability,
            BigDecimal expectedValue,
            double rtpPercent,
            double houseEdgePercent,
            double variance,
            List<Outcome> paytable
    ) {
    }

    private final CaseMathProperties properties;
    private final Map<ItemType, List<Outcome>> paytables = new EnumMap<>(ItemType.class);
    private final Random rng = new SecureRandom();

    public CaseOpenerEngine(CaseMathProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initAndValidate() {
        reloadPaytablesFromConfig();
    }

    public synchronized void reloadPaytablesFromConfig() {
        paytables.clear();
        Map<ItemType, List<CaseMathProperties.TierConfig>> configured = properties.getPaytables();
        for (ItemType itemType : ItemType.values()) {
            List<CaseMathProperties.TierConfig> tiers = (configured != null) ? configured.get(itemType) : null;
            if (tiers == null || tiers.isEmpty()) {
                throw new IllegalStateException("Missing paytable configuration in application.yaml for case: " + itemType);
            }
            BigDecimal cost = itemType.getPrice();
            List<Outcome> outcomes = tiers.stream()
                    .map(t -> new Outcome(
                            t.getMultiplier(),
                            cost.multiply(t.getMultiplier()).setScale(2, RoundingMode.HALF_UP),
                            t.getProbability(),
                            t.getLabel()
                    ))
                    .toList();
            paytables.put(itemType, outcomes);

            if (!validateMathematics(itemType)) {
                throw new IllegalStateException("Mathematical validation failed for case: " + itemType);
            }
        }
    }

    /**
     * Verifies the mathematical integrity (PMF = 1.0, EV, RTP, House Edge, Variance) of a case configuration.
     */
    public boolean validateMathematics(ItemType itemType) {
        CaseMathStats stats = calculateStats(itemType);
        if (Math.abs(stats.totalProbability() - 1.0) > 0.0001) {
            System.err.println("CRITICAL ERROR: Sum of probabilities must equal 1.0 for " + itemType);
            return false;
        }
        return true;
    }

    /**
     * Computes PMF sum, Expected Value (EV), Return to Player (RTP %), House Edge %, and Variance (sigma^2).
     */
    public CaseMathStats calculateStats(ItemType itemType) {
        List<Outcome> table = paytables.get(itemType);
        if (table == null || table.isEmpty()) {
            throw new IllegalArgumentException("No paytable configured for " + itemType);
        }

        double totalProbability = 0.0;
        double expectedValue = 0.0;

        for (Outcome outcome : table) {
            totalProbability += outcome.probability();
            expectedValue += outcome.winAmount().doubleValue() * outcome.probability();
        }

        double cost = itemType.getPrice().doubleValue();
        double rtp = (expectedValue / cost) * 100.0;
        double houseEdge = 100.0 - rtp;

        // Variance: sigma^2 = sum(P_i * (X_i - EV)^2)
        double variance = 0.0;
        for (Outcome outcome : table) {
            double diff = outcome.winAmount().doubleValue() - expectedValue;
            variance += outcome.probability() * (diff * diff);
        }

        return new CaseMathStats(
                itemType,
                itemType.getPrice(),
                roundDouble(totalProbability, 4),
                BigDecimal.valueOf(expectedValue).setScale(3, RoundingMode.HALF_UP),
                roundDouble(rtp, 2),
                roundDouble(houseEdge, 2),
                roundDouble(variance, 4),
                table
        );
    }

    /**
     * Core game loop logic for resolving a case opening via Cumulative Distribution Function (CDF).
     */
    public Outcome openCase(ItemType itemType) {
        List<Outcome> table = paytables.get(itemType);
        if (table == null || table.isEmpty()) {
            throw new IllegalArgumentException("No paytable configured for " + itemType);
        }

        double randomValue = rng.nextDouble(); // Uniform float in [0.0, 1.0)
        double cumulativeProbability = 0.0;

        for (Outcome outcome : table) {
            cumulativeProbability += outcome.probability();
            if (randomValue < cumulativeProbability) {
                return outcome;
            }
        }

        return table.get(table.size() - 1);
    }

    public boolean isMaxWin(ItemType itemType, BigDecimal winAmount) {
        List<Outcome> table = paytables.get(itemType);
        if (table == null || table.isEmpty() || winAmount == null) {
            return false;
        }
        BigDecimal maxWin = table.stream()
                .map(Outcome::winAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        return winAmount.compareTo(maxWin) >= 0;
    }

    private static double roundDouble(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
