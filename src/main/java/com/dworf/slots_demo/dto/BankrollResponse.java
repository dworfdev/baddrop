package com.dworf.slots_demo.dto;

import com.dworf.slots_demo.entity.SystemBankroll;
import com.dworf.slots_demo.service.BankrollSplit;

import java.math.BigDecimal;

public record BankrollResponse(
        BigDecimal profitPool,
        BigDecimal houseProfit,
        String floorBufferRate
) {
    public static BankrollResponse fromEntity(SystemBankroll bankroll) {
        return new BankrollResponse(
                bankroll.getPrizePool(),
                bankroll.getHouseProfit(),
                BankrollSplit.FLOOR_RATE_DISPLAY
        );
    }
}
