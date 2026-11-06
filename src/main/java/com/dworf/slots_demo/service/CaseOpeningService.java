package com.dworf.slots_demo.service;

import com.dworf.slots_demo.dto.BankrollResponse;
import com.dworf.slots_demo.entity.ItemType;
import com.dworf.slots_demo.entity.SystemBankroll;
import com.dworf.slots_demo.repository.SystemBankrollRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Split-Inflow Dynamic Bankroll Engine with Ephemeral Floor Buffer.
 * For every crate opened (cost C):
 * <ol>
 *     <li>Lock the singleton {@code system_bankroll} row (SELECT ... FOR UPDATE).</li>
 *     <li>Compute spin allocations:
 *         ΔHouse = C * 0.07 (unspendable platform net revenue, locked in DB),
 *         FloorBuffer = C * 0.33 (in-memory ephemeral buffer for this spin),
 *         ΔProfitInflow = C * 0.60 (core driver of persistent shared PrizePool).
 *     </li>
 *     <li>Pre-roll dynamic filtering:
 *         MaxAllowedDrop = PrizePool_current + FloorBuffer.
 *         ActivePool = { item ∈ CrateItems | Value(item) <= MaxAllowedDrop }.
 *     </li>
 *     <li>Re-normalize weights and roll SecureRandom over [0, W_total) to pick drop W.</li>
 *     <li>Settlement & Surplus/Deficit Reconciliation:
 *         - If W <= FloorBuffer (Scrap / Consolation Drop):
 *           Surplus = FloorBuffer - W
 *           PrizePool_new = PrizePool_current + ΔProfitInflow + Surplus
 *         - If W > FloorBuffer (Mid / High Tier Win):
 *           Deficit = W - FloorBuffer
 *           PrizePool_new = PrizePool_current + ΔProfitInflow - Deficit
 *     </li>
 *     <li>Persist updated PrizePool and HouseProfit.</li>
 * </ol>
 */
@Service
public class CaseOpeningService {

    private static final int MONEY_SCALE = 4;

    public record SpinResult(CaseOpenerEngine.Outcome outcome, boolean floorItem) {
    }

    private final SystemBankrollRepository bankrollRepository;
    private final CaseOpenerEngine caseOpenerEngine;
    private final BigDecimal initialPrizePool;

    public CaseOpeningService(
            SystemBankrollRepository bankrollRepository,
            CaseOpenerEngine caseOpenerEngine,
            @Value("${slots.bankroll.initial-prize-pool:50.0000}") BigDecimal initialPrizePool
    ) {
        this.bankrollRepository = bankrollRepository;
        this.caseOpenerEngine = caseOpenerEngine;
        this.initialPrizePool = initialPrizePool;
    }

    /** Seeds the single bankroll row (id = 1) once; safe if several instances start together. */
    @PostConstruct
    void ensureBankrollRow() {
        if (bankrollRepository.existsById(SystemBankroll.SINGLETON_ID)) {
            return;
        }
        try {
            bankrollRepository.saveAndFlush(new SystemBankroll(initialPrizePool));
        } catch (DataIntegrityViolationException alreadySeededByAnotherInstance) {
            // row exists now - nothing to do
        }
    }

    @Transactional
    public SpinResult open(ItemType caseType) {
        BigDecimal cost = caseType.getPrice();

        SystemBankroll bankroll = bankrollRepository.findByIdForUpdate(SystemBankroll.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("system_bankroll row is missing"));

        // Step 1: Compute spin allocations
        BigDecimal houseInflow = scaled(cost.multiply(BankrollSplit.HOUSE_RATE));
        BigDecimal floorBuffer = scaled(cost.multiply(BankrollSplit.FLOOR_RATE));
        BigDecimal profitInflow = scaled(cost).subtract(houseInflow).subtract(floorBuffer);

        // Add ΔHouse to cumulative house profit
        bankroll.setHouseProfit(bankroll.getHouseProfit().add(houseInflow));

        // Step 2: Pre-Roll Dynamic Filtering
        BigDecimal currentPrizePool = bankroll.getPrizePool();
        BigDecimal maxAllowedDrop = currentPrizePool.add(floorBuffer);

        // Step 3: Dynamic Weight Re-normalization & RNG Roll
        CaseOpenerEngine.Outcome won = caseOpenerEngine.drawOutcome(caseType, maxAllowedDrop);
        BigDecimal winAmount = scaled(won.winAmount());
        boolean floorItem = BankrollSplit.isFloorValue(caseType, won.winAmount());

        // Step 4: Settlement & Surplus/Deficit Reconciliation
        BigDecimal newPrizePool;
        if (winAmount.compareTo(floorBuffer) <= 0) {
            // Consolation / Scrap: item 100% absorbed by local buffer; unspent surplus rolls into pool
            BigDecimal surplus = floorBuffer.subtract(winAmount);
            newPrizePool = currentPrizePool.add(profitInflow).add(surplus);
        } else {
            // Mid / High Tier Win: buffer absorbs up to floorBuffer; remaining deficit funded by pool
            BigDecimal deficit = winAmount.subtract(floorBuffer);
            newPrizePool = currentPrizePool.add(profitInflow).subtract(deficit);
        }

        if (newPrizePool.signum() < 0) {
            // Mathematically impossible by construction; fail closed
            throw new IllegalStateException("Bankroll invariant violated: prize pool went negative for " + caseType);
        }

        // Step 5: Finalize & persist under pessimistic lock
        bankroll.setPrizePool(newPrizePool);
        bankrollRepository.save(bankroll);

        return new SpinResult(won, floorItem);
    }

    @Transactional(readOnly = true)
    public BankrollResponse getBankroll() {
        SystemBankroll bankroll = bankrollRepository.findById(SystemBankroll.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("system_bankroll row is missing"));
        return BankrollResponse.fromEntity(bankroll);
    }

    /**
     * Atomically resets the persistent prize pool and house profit to zero (0.0000)
     * under a pessimistic write lock (SELECT ... FOR UPDATE).
     */
    @Transactional
    public BankrollResponse resetBankroll() {
        SystemBankroll bankroll = bankrollRepository.findByIdForUpdate(SystemBankroll.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("system_bankroll row is missing"));
        bankroll.setPrizePool(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        bankroll.setHouseProfit(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        SystemBankroll saved = bankrollRepository.save(bankroll);
        return BankrollResponse.fromEntity(saved);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
