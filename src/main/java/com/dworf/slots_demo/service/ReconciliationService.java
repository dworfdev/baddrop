package com.dworf.slots_demo.service;

import com.dworf.slots_demo.dto.BankrollResponse;
import com.dworf.slots_demo.entity.CaseEntity;
import com.dworf.slots_demo.entity.TransactionStatus;
import com.dworf.slots_demo.entity.TransactionType;
import com.dworf.slots_demo.repository.BalanceTransactionRepository;
import com.dworf.slots_demo.repository.CaseRepository;
import com.dworf.slots_demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * H15: the books must balance.
 *   sum(balances) + value(owned cases at price) + sum(PENDING withdrawals) + prizePool + houseProfit - seed
 *       == sum(APPROVED deposits) - sum(APPROVED withdrawals)
 * Pending withdrawals are already deducted from balances, so they are an obligation that is still ours to pay.
 * Owned (unopened) cases were paid for from the balance and have not hit the bankroll yet.
 * Scans all users' inventories: fine at current scale; replace with a SQL aggregate when it grows.
 */
@Service
public class ReconciliationService {

    public record Report(BigDecimal balances, BigDecimal inventoryValue, BigDecimal pendingWithdrawals,
                         BigDecimal prizePool, BigDecimal houseProfit, BigDecimal seed,
                         BigDecimal approvedDeposits, BigDecimal approvedWithdrawals,
                         BigDecimal difference, boolean balanced) {}

    private final UserRepository userRepository;
    private final BalanceTransactionRepository transactionRepository;
    private final CaseRepository caseRepository;
    private final CaseOpeningService caseOpeningService;
    private final BigDecimal seed;

    public ReconciliationService(UserRepository userRepository, BalanceTransactionRepository transactionRepository,
                                 CaseRepository caseRepository, CaseOpeningService caseOpeningService,
                                 // same property and default as CaseOpeningService, so the seed always matches what the bankroll row started with
                                 @Value("${slots.bankroll.initial-prize-pool:50.0000}") BigDecimal seed) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.caseRepository = caseRepository;
        this.caseOpeningService = caseOpeningService;
        this.seed = seed;
    }

    @Transactional(readOnly = true)
    public Report report() {
        Map<String, BigDecimal> prices = new HashMap<>();
        for (CaseEntity c : caseRepository.findAll()) prices.put(c.getSlug(), c.getPrice());
        BigDecimal inventory = BigDecimal.ZERO;
        for (var u : userRepository.findAll()) {
            for (String slug : u.getItems()) {
                inventory = inventory.add(prices.getOrDefault(slug, BigDecimal.ZERO));
            }
        }
        BigDecimal balances = userRepository.sumAllBalances();
        BigDecimal pendingW = transactionRepository.sumAmount(TransactionType.WITHDRAW, TransactionStatus.PENDING);
        BigDecimal appD = transactionRepository.sumAmount(TransactionType.DEPOSIT, TransactionStatus.APPROVED);
        BigDecimal appW = transactionRepository.sumAmount(TransactionType.WITHDRAW, TransactionStatus.APPROVED);
        BankrollResponse b = caseOpeningService.getBankroll();
        BigDecimal left = balances.add(inventory).add(pendingW).add(b.profitPool()).add(b.houseProfit()).subtract(seed);
        BigDecimal right = appD.subtract(appW);
        BigDecimal diff = left.subtract(right);
        return new Report(balances, inventory, pendingW, b.profitPool(), b.houseProfit(), seed, appD, appW,
                diff, diff.compareTo(BigDecimal.ZERO) == 0);
    }
}
