package com.dworf.slots_demo.dto;

import com.dworf.slots_demo.service.CaseOpenerEngine;

import java.math.BigDecimal;
import java.util.List;

public record OpenCaseResponse(
        String username,
        String openedCase,
        BigDecimal caseCost,
        BigDecimal wonMultiplier,
        BigDecimal wonAmount,
        boolean jackpot,
        String outcomeTier,
        BigDecimal newBalance,
        List<String> remainingItems,
        BigDecimal expectedValue,
        double rtpPercent,
        double variance,
        List<CaseOpenerEngine.PaytableTier> paytable,
        int quantity,
        BigDecimal totalWonAmount,
        List<SpinOutcome> spins
) {
    public record SpinOutcome(
            BigDecimal wonAmount,
            BigDecimal wonMultiplier,
            String outcomeTier,
            boolean jackpot
    ) {
    }

    public OpenCaseResponse(
            String username,
            String openedCase,
            BigDecimal caseCost,
            BigDecimal wonMultiplier,
            BigDecimal wonAmount,
            boolean jackpot,
            String outcomeTier,
            BigDecimal newBalance,
            List<String> remainingItems,
            BigDecimal expectedValue,
            double rtpPercent,
            double variance,
            List<CaseOpenerEngine.PaytableTier> paytable
    ) {
        this(
                username,
                openedCase,
                caseCost,
                wonMultiplier,
                wonAmount,
                jackpot,
                outcomeTier,
                newBalance,
                remainingItems,
                expectedValue,
                rtpPercent,
                variance,
                paytable,
                1,
                wonAmount,
                List.of(new SpinOutcome(wonAmount, wonMultiplier, outcomeTier, jackpot))
        );
    }
}
