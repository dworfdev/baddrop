package com.dworf.slots_demo.dto;

public record ConfirmDepositRequest(
        String username,
        String password,
        String txHash
) {
    public ConfirmDepositRequest(String username, String password) {
        this(username, password, null);
    }
}

