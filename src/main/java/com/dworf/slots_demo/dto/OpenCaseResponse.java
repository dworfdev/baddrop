package com.dworf.slots_demo.dto;

import com.dworf.slots_demo.entity.ItemType;

import java.math.BigDecimal;
import java.util.List;

public record OpenCaseResponse(
        String username,
        ItemType openedCase,
        BigDecimal caseCost,
        BigDecimal wonAmount,
        boolean jackpot,
        String outcomeTier,
        BigDecimal newBalance,
        List<ItemType> remainingItems,
        BigDecimal expectedValue,
        double rtpPercent,
        double variance
) {
}

