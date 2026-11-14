package com.dworf.slots_demo.service;

import com.dworf.slots_demo.dto.BankrollResponse;
import com.dworf.slots_demo.entity.OwnerDraw;
import com.dworf.slots_demo.entity.SystemBankroll;
import com.dworf.slots_demo.repository.OwnerDrawRepository;
import com.dworf.slots_demo.repository.SystemBankrollRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monthly close: the owner takes house profit out of the wallet and records it here. Only house profit can be
 * drawn; the prize pool (money the players' future wins come from) is never touched. Every draw is stored, so
 * reconciliation keeps balancing: it subtracts the sum of draws from the money that came in.
 */
@Service
public class SettlementService {
    private static final int SCALE = 4;

    private final SystemBankrollRepository bankrollRepository;
    private final OwnerDrawRepository draws;
    private final AuditService audit;

    public SettlementService(SystemBankrollRepository bankrollRepository, OwnerDrawRepository draws, AuditService audit) {
        this.bankrollRepository = bankrollRepository;
        this.draws = draws;
        this.audit = audit;
    }

    public record Settled(Long drawId, BigDecimal amount, BankrollResponse bankroll) {}

    /** Same row lock as every case opening, so a settlement cannot interleave with a spin. */
    @Transactional
    public Settled settleHouseProfit(BigDecimal rawAmount, String note, String adminName, String ip) {
        return take(false, rawAmount, note, adminName, ip);
    }

    /**
     * Manual correction: removes money from the prize pool (e.g. a deposit was approved by mistake or the books
     * did not match). Recorded as an owner draw of source POOL so reconciliation keeps balancing.
     */
    @Transactional
    public Settled deductPrizePool(BigDecimal rawAmount, String note, String adminName, String ip) {
        return take(true, rawAmount, note, adminName, ip);
    }

    private Settled take(boolean fromPool, BigDecimal rawAmount, String note, String adminName, String ip) {
        if (rawAmount == null || rawAmount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }
        if (rawAmount.stripTrailingZeros().scale() > SCALE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount has too many decimal places");
        }
        BigDecimal amount = rawAmount.setScale(SCALE, RoundingMode.UNNECESSARY);
        SystemBankroll bankroll = bankrollRepository.findByIdForUpdate(SystemBankroll.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("system_bankroll row is missing"));
        BigDecimal available = fromPool ? bankroll.getPrizePool() : bankroll.getHouseProfit();
        if (amount.compareTo(available) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Amount exceeds the " + (fromPool ? "prize pool" : "house profit") + " available ("
                            + available.toPlainString() + ")");
        }
        String cleanNote = note == null ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 200) {
            cleanNote = cleanNote.substring(0, 200);
        }
        if (fromPool) {
            bankroll.setPrizePool(bankroll.getPrizePool().subtract(amount));
        } else {
            bankroll.setHouseProfit(bankroll.getHouseProfit().subtract(amount));
        }
        bankrollRepository.save(bankroll);
        OwnerDraw draw = draws.save(new OwnerDraw(amount, cleanNote == null || cleanNote.isEmpty() ? null : cleanNote,
                adminName, fromPool ? "POOL" : "HOUSE"));
        audit.record(adminName, fromPool ? "PRIZE_POOL_DEDUCTED" : "HOUSE_PROFIT_SETTLED", String.valueOf(draw.getId()),
                "amount=" + amount.toPlainString() + " poolAfter=" + bankroll.getPrizePool().toPlainString()
                        + " houseProfitAfter=" + bankroll.getHouseProfit().toPlainString(), ip);
        return new Settled(draw.getId(), amount, BankrollResponse.fromEntity(bankroll));
    }
}
