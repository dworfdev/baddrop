package com.dworf.slots_demo.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LiveFeedEventResponse(
        String username,
        BigDecimal wonAmount,
        String caseName,
        String outcomeTier,
        Instant timestamp
) {
}
