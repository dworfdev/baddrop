package com.dworf.slots_demo.dto;

/** externalTxHash is mandatory when approving a DEPOSIT (the on-chain hash the admin verified). */
public record ApproveTransactionRequest(String externalTxHash) {
}
