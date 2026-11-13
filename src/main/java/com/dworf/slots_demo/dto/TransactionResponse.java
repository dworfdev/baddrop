package com.dworf.slots_demo.dto;

import com.dworf.slots_demo.entity.BalanceTransaction;
import com.dworf.slots_demo.entity.TransactionStatus;
import com.dworf.slots_demo.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String txHash,
        String username,
        TransactionType type,
        BigDecimal amount,
        TransactionStatus status,
        Instant createdAt,
        Instant expiresAt,
        String walletAddress,
        String memo,
        String receipt
) {
    public static TransactionResponse fromEntity(BalanceTransaction tx) {
        return new TransactionResponse(
                tx.getTxHash(),
                tx.getUser().getUsername(),
                tx.getType(),
                tx.getAmount(),
                tx.getStatus(),
                tx.getCreatedAt(),
                tx.getExpiresAt(),
                tx.getWalletAddress(),
                tx.getDepositMemo(),
                tx.getExternalTxHash()
        );
    }
}

