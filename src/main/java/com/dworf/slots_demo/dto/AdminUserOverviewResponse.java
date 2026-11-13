package com.dworf.slots_demo.dto;

import com.dworf.slots_demo.entity.User;

import java.math.BigDecimal;
import java.util.List;

public record AdminUserOverviewResponse(
        Long id,
        Long telegramId,
        String username,
        BigDecimal balance,
        List<String> items,
        List<TransactionResponse> pendingTransactions,
        List<TransactionResponse> transactionHistory
) {
    public static AdminUserOverviewResponse from(
            User user,
            List<TransactionResponse> pendingTransactions,
            List<TransactionResponse> transactionHistory
    ) {
        return new AdminUserOverviewResponse(
                user.getId(),
                user.getTelegramId(),
                user.getUsername(),
                user.getBalance(),
                List.copyOf(user.getItems()),
                pendingTransactions,
                transactionHistory
        );
    }
}
