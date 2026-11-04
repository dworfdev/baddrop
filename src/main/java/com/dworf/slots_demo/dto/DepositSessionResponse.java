package com.dworf.slots_demo.dto;

import com.dworf.slots_demo.entity.BalanceTransaction;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record DepositSessionResponse(
        boolean active,
        boolean hasPendingTransaction,
        boolean hasApprovedDeposit,
        String txHash,
        String username,
        BigDecimal amount,
        String currency,
        String chain,
        String address,
        String adminUsername,
        Instant createdAt,
        Instant expiresAt,
        long remainingSeconds
) {
    public static final String DEFAULT_CURRENCY = "USDT";
    public static final String DEFAULT_CHAIN = "Ton";
    public static final String DEFAULT_ADDRESS = "UQCys13azAWTFuERWwjc_5sqKZxQEhJhbtGGaiyp2Sf55Hrg";
    public static final String DEFAULT_ADMIN_USERNAME = "chromakid";

    public static DepositSessionResponse inactive(
            String username,
            boolean hasPendingTransaction,
            boolean hasApprovedDeposit
    ) {
        return new DepositSessionResponse(
                false,
                hasPendingTransaction,
                hasApprovedDeposit,
                null,
                username,
                BigDecimal.ZERO,
                DEFAULT_CURRENCY,
                DEFAULT_CHAIN,
                DEFAULT_ADDRESS,
                DEFAULT_ADMIN_USERNAME,
                null,
                null,
                0L
        );
    }

    public static DepositSessionResponse fromEntity(
            BalanceTransaction tx,
            boolean hasPendingTransaction,
            boolean hasApprovedDeposit
    ) {
        Instant now = Instant.now();
        Instant exp = tx.getExpiresAt() != null ? tx.getExpiresAt() : now;
        long remaining = Math.max(0L, Duration.between(now, exp).getSeconds());
        return new DepositSessionResponse(
                remaining > 0,
                hasPendingTransaction,
                hasApprovedDeposit,
                tx.getTxHash(),
                tx.getUser().getUsername(),
                tx.getAmount(),
                DEFAULT_CURRENCY,
                DEFAULT_CHAIN,
                DEFAULT_ADDRESS,
                DEFAULT_ADMIN_USERNAME,
                tx.getCreatedAt(),
                tx.getExpiresAt(),
                remaining
        );
    }
}
