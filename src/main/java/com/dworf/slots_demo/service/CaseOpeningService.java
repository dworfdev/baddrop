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
import java.util.ArrayList;
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

    public static int validateQuantity(Integer rawQuantity) {
        int qty = rawQuantity == null ? 1 : rawQuantity;
        if (qty < 1 || qty > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity must be between 1 and 5"
            );
        }
        return qty;
    }

    /**
     * Buys one or more cases (1..5) for the given user:
     * 1. Finds the case in DB by slug.
     * 2. Verifies {@code is_active} is true.
     * 3. Deducts {@code case.price * quantity} from user balance.
     * 4. Appends {@code quantity} copies of {@code case.slug} to {@code user.items}.
     */
    @Transactional
    public UserResponse buyCase(User user, String rawSlug) {
        return buyCase(user, rawSlug, 1);
    }

    @Transactional
    public UserResponse buyCase(User user, String rawSlug, int rawQuantity) {
        int quantity = validateQuantity(rawQuantity);
        CaseEntity caseEntity = findCaseBySlugOrThrow(rawSlug);
        if (!caseEntity.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Case '" + caseEntity.getSlug() + "' is not active"
            );
        }

        BigDecimal price = caseEntity.getPrice();
        BigDecimal totalCost = price.multiply(BigDecimal.valueOf(quantity));
        if (user.getBalance().compareTo(totalCost) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient funds to buy " + (quantity > 1 ? quantity + "x " : "")
                            + caseEntity.getSlug() + " ($" + totalCost + "). Current balance: $" + user.getBalance()
            );
        }

        user.setBalance(user.getBalance().subtract(totalCost));
        for (int i = 0; i < quantity; i++) {
            user.getItems().add(caseEntity.getSlug());
        }
        return UserResponse.fromEntity(userRepository.save(user));
    }

    /**
     * Opens one or more owned cases (1..5) for the given user:
     * 1. Verifies the user owns at least {@code quantity} copies of the case slug in {@code user.items} and removes them.
     * 2. Parses {@code paytable_json} for that case and passes the parsed {@link List} to {@link CaseOpenerEngine}.
     * 3. Executes {@code quantity} independent spins under pessimistic lock.
     * 4. Credits the combined total won amount to user balance.
     */
    @Transactional
    public OpenCaseResponse openCaseForUser(User user, String rawSlug) {
        return openCaseForUser(user, rawSlug, 1);
    }

    @Transactional
    public OpenCaseResponse openCaseForUser(User user, String rawSlug, int rawQuantity) {
        int quantity = validateQuantity(rawQuantity);
        CaseEntity caseEntity = findCaseBySlugOrThrow(rawSlug);
        String slug = caseEntity.getSlug();

        long ownedCount = user.getItems().stream().filter(slug::equals).count();
        if (ownedCount < quantity) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    quantity == 1
                            ? "You do not own any " + slug + " in your inventory"
                            : "You only own " + ownedCount + "x " + slug + " in your inventory (requested x" + quantity + ")"
            );
        }
        for (int i = 0; i < quantity; i++) {
            user.getItems().remove(slug);
        }

        return executeMultiSpinAndCreditUser(user, caseEntity, quantity);
    }

    /**
     * Instantly opens one or more cases (1..5) directly from the marketplace for the given user without touching inventory:
     * 1. Finds the case in DB by slug and verifies {@code is_active} is true.
     * 2. Validates {@code user.balance >= case.price * quantity} (throws HTTP 400 if insufficient).
     * 3. Atomically deducts {@code case.price * quantity} from user balance.
     * 4. Runs {@code quantity} independent spin calculations via {@link CaseOpenerEngine} and bankroll accounting.
     * 5. Credits the combined won payout back to user balance and saves user state.
     * 6. Returns the {@link OpenCaseResponse} payload including individual {@code spins} and {@code totalWonAmount}.
     */
    @Transactional
    public OpenCaseResponse instantOpenCase(User user, String rawSlug) {
        return instantOpenCase(user, rawSlug, 1);
    }

    @Transactional
    public OpenCaseResponse instantOpenCase(User user, String rawSlug, int rawQuantity) {
        int quantity = validateQuantity(rawQuantity);
        CaseEntity caseEntity = findCaseBySlugOrThrow(rawSlug);
        if (!caseEntity.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Case '" + caseEntity.getSlug() + "' is not active"
            );
        }

        String slug = caseEntity.getSlug();
        BigDecimal price = caseEntity.getPrice();
        BigDecimal totalCost = price.multiply(BigDecimal.valueOf(quantity));
        if (user.getBalance().compareTo(totalCost) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient funds to open " + (quantity > 1 ? quantity + "x " : "")
                            + slug + " ($" + totalCost + "). Current balance: $" + user.getBalance()
            );
        }

        // Deduct total crate cost before spinning
        user.setBalance(user.getBalance().subtract(totalCost));

        return executeMultiSpinAndCreditUser(user, caseEntity, quantity);
    }

    private OpenCaseResponse executeMultiSpinAndCreditUser(User user, CaseEntity caseEntity, int quantity) {
        String slug = caseEntity.getSlug();
        BigDecimal price = caseEntity.getPrice();
        List<CaseOpenerEngine.PaytableTier> paytableTiers = caseOpenerEngine.parsePaytableJson(
                caseEntity.getPaytableJson()
        );
        List<CaseOpenerEngine.Outcome> paytable = caseOpenerEngine.toOutcomes(
                price,
                paytableTiers
        );
        CaseOpenerEngine.CaseMathStats stats = caseOpenerEngine.calculateStats(slug, price, paytable);

        List<OpenCaseResponse.SpinOutcome> spins = new ArrayList<>(quantity);
        BigDecimal totalWonAmount = BigDecimal.ZERO;
        boolean anyJackpot = false;

        for (int i = 0; i < quantity; i++) {
            SpinResult spinResult = openWithPaytable(slug, price, paytable);
            CaseOpenerEngine.Outcome outcome = spinResult.outcome();
            boolean isJackpot = caseOpenerEngine.isMaxWin(paytable, outcome.winAmount());
            if (isJackpot) {
                anyJackpot = true;
            }
            totalWonAmount = totalWonAmount.add(outcome.winAmount());
            spins.add(new OpenCaseResponse.SpinOutcome(
                    outcome.winAmount(),
                    outcome.multiplier(),
                    outcome.tierLabel(),
                    isJackpot
            ));
        }

        user.setBalance(user.getBalance().add(totalWonAmount));
        User savedUser = userRepository.save(user);

        OpenCaseResponse.SpinOutcome firstSpin = spins.get(0);
        return new OpenCaseResponse(
                savedUser.getUsername(),
                slug,
                price,
                firstSpin.wonMultiplier(),
                totalWonAmount,
                anyJackpot,
                firstSpin.outcomeTier(),
                savedUser.getBalance(),
                List.copyOf(savedUser.getItems()),
                stats.expectedValue(),
                stats.rtpPercent(),
                stats.variance(),
                paytableTiers,
                quantity,
                totalWonAmount,
                List.copyOf(spins)
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
