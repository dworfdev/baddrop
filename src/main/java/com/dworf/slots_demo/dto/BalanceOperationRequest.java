package com.dworf.slots_demo.dto;

import java.math.BigDecimal;

public record BalanceOperationRequest(
        String username,
        String password,
        BigDecimal amount,
        String walletAddress
) {
    public BalanceOperationRequest(String username, String password, BigDecimal amount) {
        this(username, password, amount, null);
    }
}


