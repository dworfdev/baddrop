package com.dworf.slots_demo.dto;

/** The client must echo the account's own username, so a stray request cannot delete anything. */
public record DeleteAccountRequest(String confirm) {
}
