package com.dworf.slots_demo.dto;

/** Local testing only (slots.auth.dev-login-enabled=true): log in as a made-up player without Telegram. */
public record DevLoginRequest(String username) {
}
