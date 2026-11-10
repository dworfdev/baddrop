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
        List<CaseOpenerEngine.PaytableTier> paytable
) {
}
