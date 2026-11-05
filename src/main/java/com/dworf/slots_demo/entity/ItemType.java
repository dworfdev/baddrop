package com.dworf.slots_demo.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.math.BigDecimal;
import java.util.Arrays;

public enum ItemType {
    PEPE_IRON(new BigDecimal("2.00")),
    SHIBA_INU_GOLD(new BigDecimal("5.00")),
    DOGE_AMETHYST(new BigDecimal("11.00"));

    private final BigDecimal price;

    ItemType(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getPrice() {
        return price;
    }

    /**
     * Parses a case identifier coming from the client. The name must match a constant
     * (case, spaces and dashes are tolerated: "pepe-iron" -> PEPE_IRON); no legacy aliases.
     */
    @JsonCreator
    public static ItemType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Item name must not be blank.");
        }
        String normalized = value.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown case: '" + value + "'. Allowed cases: " + Arrays.toString(values())
            );
        }
    }
}