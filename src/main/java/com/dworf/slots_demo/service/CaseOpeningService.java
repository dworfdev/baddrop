package com.dworf.slots_demo.service;

import com.dworf.slots_demo.dto.BankrollResponse;
import com.dworf.slots_demo.dto.CaseResponse;
import com.dworf.slots_demo.dto.OpenCaseResponse;
import com.dworf.slots_demo.dto.UserResponse;
import com.dworf.slots_demo.entity.CaseEntity;
import com.dworf.slots_demo.entity.SystemBankroll;
import com.dworf.slots_demo.entity.User;
import com.dworf.slots_demo.repository.CaseRepository;
import com.dworf.slots_demo.repository.SystemBankrollRepository;
import com.dworf.slots_demo.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Split-Inflow Dynamic Bankroll Engine & Database-Backed Case Service.
 * Handles buying, opening, and bankroll coordination with {@link CaseOpenerEngine}.
 */
@Service
public class CaseOpeningService {

    private static final int MONEY_SCALE = 4;

    public record SpinResult(CaseOpenerEngine.Outcome outcome, boolean floorItem) {
    }

    private final SystemBankrollRepository bankrollRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseOpenerEngine caseOpenerEngine;
    private final BigDecimal initialPrizePool;

    public CaseOpeningService(
            SystemBankrollRepository bankrollRepository,
            CaseRepository caseRepository,
            UserRepository userRepository,
            CaseOpenerEngine caseOpenerEngine,
            @Value("${slots.bankroll.initial-prize-pool:50.0000}") BigDecimal initialPrizePool
    ) {
        this.bankrollRepository = bankrollRepository;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.caseOpenerEngine = caseOpenerEngine;
        this.initialPrizePool = initialPrizePool;
    }

    /** Seeds the singleton bankroll row (id = 1) once; safe if several instances start together. */
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

    public static String normalizeSlug(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case slug must be provided");
        }
        return raw.trim().toLowerCase().replace("_", "-").replace(" ", "-");
    }

    @Transactional(readOnly = true)
    public CaseEntity findCaseBySlugOrThrow(String rawSlug) {
        if (rawSlug == null || rawSlug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case slug must be provided");
        }
        String trimmed = rawSlug.trim();
        return caseRepository.findBySlug(trimmed)
                .or(() -> caseRepository.findBySlug(normalizeSlug(trimmed)))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown case: '" + rawSlug + "'"
                ));
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> getActiveCases() {
        return caseRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                .stream()
                .map(CaseResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CaseEntity> getAllActiveCaseEntities() {
        return caseRepository.findByIsActiveTrueOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<CaseOpenerEngine.CaseMathStats> getAllCasesMathStats() {
        return caseRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                .stream()
                .map(c -> {
                    List<CaseOpenerEngine.Outcome> paytable = caseOpenerEngine.parseOutcomes(c.getPrice(), c.getPaytableJson());
                    return caseOpenerEngine.calculateStats(c.getSlug(), c.getPrice(), paytable);
                })
                .toList();
    }

    /**
     * Buys a case for the given user:
     * 1. Finds the case in DB by slug.
     * 2. Verifies {@code is_active} is true.
     * 3. Deducts {@code case.price} from user balance.
     * 4. Appends {@code case.slug} to {@code user.items}.
     */
    @Transactional
    public UserResponse buyCase(User user, String rawSlug) {
        CaseEntity caseEntity = findCaseBySlugOrThrow(rawSlug);
        if (!caseEntity.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Case '" + caseEntity.getSlug() + "' is not active"
            );
        }

        BigDecimal price = caseEntity.getPrice();
        if (user.getBalance().compareTo(price) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient funds to buy " + caseEntity.getSlug() + " ($" + price + "). Current balance: $" + user.getBalance()
            );
        }

        user.setBalance(user.getBalance().subtract(price));
        user.getItems().add(caseEntity.getSlug());
        return UserResponse.fromEntity(userRepository.save(user));
    }

    /**
     * Opens an owned case for the given user:
     * 1. Verifies the user owns the case slug in {@code user.items} and removes one instance.
     * 2. Parses {@code paytable_json} for that case and passes the parsed {@link List} to {@link CaseOpenerEngine}.
     * 3. Records bankroll accounting under pessimistic lock.
     * 4. Credits the won amount to user balance.
     */
    @Transactional
    public OpenCaseResponse openCaseForUser(User user, String rawSlug) {
        CaseEntity caseEntity = findCaseBySlugOrThrow(rawSlug);
        String slug = caseEntity.getSlug();

        boolean removed = user.getItems().remove(slug);
        if (!removed) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You do not own any " + slug + " in your inventory"
            );
        }

        List<CaseOpenerEngine.PaytableTier> paytableTiers = caseOpenerEngine.parsePaytableJson(
                caseEntity.getPaytableJson()
        );
        List<CaseOpenerEngine.Outcome> paytable = caseOpenerEngine.toOutcomes(
                caseEntity.getPrice(),
                paytableTiers
        );

        SpinResult spinResult = openWithPaytable(caseEntity.getSlug(), caseEntity.getPrice(), paytable);
        CaseOpenerEngine.Outcome outcome = spinResult.outcome();
        CaseOpenerEngine.CaseMathStats stats = caseOpenerEngine.calculateStats(slug, caseEntity.getPrice(), paytable);
        boolean isJackpot = caseOpenerEngine.isMaxWin(paytable, outcome.winAmount());

        user.setBalance(user.getBalance().add(outcome.winAmount()));
        User savedUser = userRepository.save(user);

        return new OpenCaseResponse(
                savedUser.getUsername(),
                slug,
                caseEntity.getPrice(),
                outcome.multiplier(),
                outcome.winAmount(),
                isJackpot,
                outcome.tierLabel(),
                savedUser.getBalance(),
                List.copyOf(savedUser.getItems()),
                stats.expectedValue(),
                stats.rtpPercent(),
                stats.variance(),
                paytableTiers
        );
    }

    /**
     * Executes a single bankroll-tracked spin for the case identified by {@code rawSlug}.
     */
    @Transactional
    public SpinResult open(String rawSlug) {
        CaseEntity caseEntity = findCaseBySlugOrThrow(rawSlug);
        List<CaseOpenerEngine.Outcome> paytable = caseOpenerEngine.parseOutcomes(
                caseEntity.getPrice(),
                caseEntity.getPaytableJson()
        );
        return openWithPaytable(caseEntity.getSlug(), caseEntity.getPrice(), paytable);
    }

    @Transactional
    public SpinResult openWithPaytable(String slug, BigDecimal cost, List<CaseOpenerEngine.Outcome> paytable) {
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

        // Step 3: Dynamic Weight Re-normalization & RNG Roll from parsed paytable list
        CaseOpenerEngine.Outcome won = caseOpenerEngine.drawOutcome(paytable, maxAllowedDrop);
        BigDecimal winAmount = scaled(won.winAmount());
        boolean floorItem = BankrollSplit.isFloorValue(cost, won.winAmount());

        // Step 4: Settlement & Surplus/Deficit Reconciliation
        BigDecimal newPrizePool;
        if (winAmount.compareTo(floorBuffer) <= 0) {
            BigDecimal surplus = floorBuffer.subtract(winAmount);
            newPrizePool = currentPrizePool.add(profitInflow).add(surplus);
        } else {
            BigDecimal deficit = winAmount.subtract(floorBuffer);
            newPrizePool = currentPrizePool.add(profitInflow).subtract(deficit);
        }

        if (newPrizePool.signum() < 0) {
            throw new IllegalStateException("Bankroll invariant violated: prize pool went negative for " + slug);
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
