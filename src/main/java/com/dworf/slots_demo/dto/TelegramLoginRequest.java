package com.dworf.slots_demo.dto;

/** The raw signed query string from {@code Telegram.WebApp.initData}. Never logged. */
public record TelegramLoginRequest(String initData) {
    @Override
    public String toString() {
        return "TelegramLoginRequest[initData=<hidden>]";
    }
}
