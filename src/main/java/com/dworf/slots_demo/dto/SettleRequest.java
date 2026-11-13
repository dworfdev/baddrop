package com.dworf.slots_demo.dto;

import java.math.BigDecimal;

/** Monthly close: how much house profit was withdrawn from the wallet, plus an optional note. */
public record SettleRequest(BigDecimal amount, String note) {
}
