package com.dworf.slots_demo.dto;

import java.math.BigDecimal;

public record WithdrawRequest(BigDecimal amount, String walletAddress) {
}
