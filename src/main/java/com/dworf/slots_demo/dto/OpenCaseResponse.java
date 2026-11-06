package com.dworf.slots_demo.dto;

import java.math.BigDecimal;
import java.util.List;

public record OpenCaseResponse(
        String username,
        String openedCase,
        BigDecimal caseCost,
        BigDecimal wonAmount,
        boolean jackpot,
        String outcomeTier,
        BigDecimal newBalance,
        List<String> remainingItems,
        BigDecimal expectedValue,
        double rtpPercent,
        double variance
) {
}
