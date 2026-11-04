package com.dworf.slots_demo.dto;

import java.math.BigDecimal;

public record BalanceResponse(
        String username,
        BigDecimal balance
) {
}

