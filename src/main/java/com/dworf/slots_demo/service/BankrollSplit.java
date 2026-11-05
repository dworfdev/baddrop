package com.dworf.slots_demo.service;

import com.dworf.slots_demo.entity.ItemType;

import java.math.BigDecimal;

/**
 * Split-Inflow Bankroll Model constants. Every spin of a case costing C is split into:
 * <pre>
 *   house edge   : C * 0.07  (unspendable platform revenue)
 *   floor buffer : C * 0.33  (funds drops worth <= C * 0.33)
 *   profit pool  : C * 0.60  (funds drops worth  > C * 0.33)
 * </pre>
 */
public final class BankrollSplit {

    public static final BigDecimal HOUSE_RATE = new BigDecimal("0.07");
    public static final BigDecimal FLOOR_RATE = new BigDecimal("0.33");
    public static final BigDecimal POOL_RATE = new BigDecimal("0.60");
    public static final String FLOOR_RATE_DISPLAY = "33%";

    private BankrollSplit() {
    }

    /** Drops with value <= this threshold are "floor" (consolation) items. */
    public static BigDecimal floorThreshold(ItemType caseType) {
        return caseType.getPrice().multiply(FLOOR_RATE);
    }

    public static boolean isFloorValue(ItemType caseType, BigDecimal value) {
        return value.compareTo(floorThreshold(caseType)) <= 0;
    }
}

