package com.dworf.slots_demo.dto;

/**
 * txHash = the deposit session id the server issued. receipt = what the player pastes after paying: the on-chain
 * transaction hash or an explorer link (normalised server-side; each chain transaction can back only one deposit).
 */
public record ConfirmDepositRequest(String txHash, String receipt) {
}
