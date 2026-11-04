package com.dworf.slots_demo.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.math.BigDecimal;

public enum ItemType {
    IRON_CRATE(new BigDecimal("2.00")),
    SILVER_CRATE(new BigDecimal("5.00")),
    GOLD_CRATE(new BigDecimal("11.00"));

    private final BigDecimal price;

    ItemType(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getPrice() {
        return price;
    }

    @JsonCreator
    public static ItemType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Item name must not be blank.");
        }
        String normalized = value.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        return switch (normalized) {
            case "IRON", "IRON_CRATE", "IRON_CASE", "PEPE_IRON", "CASE_PEPE_IRON" -> IRON_CRATE;
            case "SILVER", "SILVER_CRATE", "SILVER_CASE", "SHIBA_INU_GOLD", "CASE_SHIBA_INU_GOLD" -> SILVER_CRATE;
            case "GOLD", "GOLD_CRATE", "GOLD_CASE", "DOGE_AMETHYST", "CASE_DOGE_AMETHYST" -> GOLD_CRATE;
            default -> throw new IllegalArgumentException(
                    "Unknown case: '" + value + "'. Allowed cases: PEPE_IRON (IRON_CRATE), SHIBA_INU_GOLD (SILVER_CRATE), DOGE_AMETHYST (GOLD_CRATE)"
            );
        };
    }
}
