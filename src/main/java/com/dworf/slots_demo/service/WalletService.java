package com.dworf.slots_demo.service;

import com.dworf.slots_demo.dto.DepositSessionResponse;
import com.dworf.slots_demo.dto.UserResponse;
import com.dworf.slots_demo.entity.BalanceTransaction;
import com.dworf.slots_demo.entity.TransactionStatus;
import com.dworf.slots_demo.entity.TransactionType;
import com.dworf.slots_demo.entity.User;
import com.dworf.slots_demo.security.TonTxHash;
import com.dworf.slots_demo.repository.BalanceTransactionRepository;
import com.dworf.slots_demo.repository.UserRepository;
import com.dworf.slots_demo.security.Money;
import com.dworf.slots_demo.security.TonAddressValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Player-side money flows. Every method locks the user row first (global lock order: user -> transaction -> bankroll). */
@Service
public class WalletService {

    public static final long DEPOSIT_TIMER_SECONDS = 15 * 60L;
    public static final BigDecimal MIN_DEPOSIT_AMOUNT = new BigDecimal("1.00");
    public static final BigDecimal MIN_WITHDRAW_AMOUNT = new BigDecimal("2.50");

    private final UserRepository userRepository;
    private final BalanceTransactionRepository transactionRepository;
    private final IdempotencyService idempotency;
    private final Clock clock;
    private final String depositAdminUsername;

    public WalletService(UserRepository userRepository, BalanceTransactionRepository transactionRepository,
                         IdempotencyService idempotency, Clock clock,
                         @Value("${app.deposit.admin-username:chromakid}") String depositAdminUsername) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.idempotency = idempotency;
        this.clock = clock;
        this.depositAdminUsername = depositAdminUsername;
    }

    public String getDepositAdminUsername() {
        return depositAdminUsername;
    }

    @Transactional
    public DepositSessionResponse startDeposit(Long userId, BigDecimal rawAmount, String idempotencyKey) {
        User user = lockActive(userId);
        idempotency.claim(userId, idempotencyKey, "DEPOSIT_START");
        BigDecimal amount = money(rawAmount);
        if (amount.compareTo(MIN_DEPOSIT_AMOUNT) < 0) {
            throw bad("Minimum deposit amount is $1.00 USDT");
        }
        if (transactionRepository.existsByUserAndStatus(user, TransactionStatus.PENDING)) {
            throw conflict("You already have a pending transaction awaiting admin review");
        }
        for (BalanceTransaction old : transactionRepository
                .findByUserAndStatusOrderByCreatedAtDesc(user, TransactionStatus.AWAITING_PAYMENT)) {
            old.setStatus(TransactionStatus.EXPIRED);
            transactionRepository.save(old);
        }
        BalanceTransaction saved = transactionRepository.save(new BalanceTransaction(
                user, TransactionType.DEPOSIT, amount, TransactionStatus.AWAITING_PAYMENT,
                clock.instant().plusSeconds(DEPOSIT_TIMER_SECONDS), DepositSessionResponse.DEFAULT_ADDRESS));
        return DepositSessionResponse.fromEntity(saved, false, hasApprovedDeposit(user), depositAdminUsername);
    }

    @Transactional
    public DepositSessionResponse activeDeposit(Long userId) {
        User user = lockActive(userId);
        boolean hasPending = transactionRepository.existsByUserAndStatus(user, TransactionStatus.PENDING);
        boolean hasApproved = hasApprovedDeposit(user);
        Instant now = clock.instant();
        BalanceTransaction active = null;
        for (BalanceTransaction tx : transactionRepository
                .findByUserAndStatusOrderByCreatedAtDesc(user, TransactionStatus.AWAITING_PAYMENT)) {
            boolean expired = tx.getExpiresAt() != null && now.isAfter(tx.getExpiresAt());
            if (expired || active != null) {
                tx.setStatus(TransactionStatus.EXPIRED);
                transactionRepository.save(tx);
            } else {
                active = tx;
            }
        }
        return active == null
                ? DepositSessionResponse.inactive(user.getUsername(), hasPending, hasApproved, depositAdminUsername)
                : DepositSessionResponse.fromEntity(active, hasPending, hasApproved, depositAdminUsername);
    }

    /** Player abandons the open payment window (wrong amount etc.). Only AWAITING_PAYMENT rows are touched. */
    @Transactional
    public DepositSessionResponse cancelDeposit(Long userId) {
        User user = lockActive(userId);
        for (BalanceTransaction tx : transactionRepository
                .findByUserAndStatusOrderByCreatedAtDesc(user, TransactionStatus.AWAITING_PAYMENT)) {
            tx.setStatus(TransactionStatus.EXPIRED);
            transactionRepository.save(tx);
        }
        return DepositSessionResponse.inactive(user.getUsername(),
                transactionRepository.existsByUserAndStatus(user, TransactionStatus.PENDING),
                hasApprovedDeposit(user), depositAdminUsername);
    }

    @Transactional
    public UserResponse confirmDeposit(Long userId, String txHash, String receipt) {
        User user = lockActive(userId);
        if (txHash == null || txHash.isBlank()) {
            throw bad("Transaction hash must be provided");
        }
        String chainHash;
        try {
            chainHash = TonTxHash.normalize(receipt);
        } catch (IllegalArgumentException e) {
            throw bad(e.getMessage());
        }
        if (transactionRepository.existsByUserAndStatus(user, TransactionStatus.PENDING)) {
            throw conflict("You already have a pending transaction awaiting admin review");
        }
        List<BalanceTransaction> awaiting = transactionRepository
                .findByUserAndStatusOrderByCreatedAtDesc(user, TransactionStatus.AWAITING_PAYMENT);
        if (awaiting.isEmpty()) {
            throw conflict("No active deposit session found. Please start a new deposit.");
        }
        BalanceTransaction tx = awaiting.get(0);
        if (!tx.getTxHash().equals(txHash.trim())) {
            throw conflict("Transaction hash mismatch or session superseded");
        }
        Instant now = clock.instant();
        if (tx.getExpiresAt() != null && now.isAfter(tx.getExpiresAt())) {
            tx.setStatus(TransactionStatus.EXPIRED);
            transactionRepository.save(tx);
            throw bad("Deposit 15-minute timer has expired. Please start a new deposit.");
        }
        if (transactionRepository.existsByExternalTxHash(chainHash)) {
            throw conflict("This payment was already submitted for another deposit");
        }
        // flushed (and checked against the unique index) by the flushAutomatically update below
        tx.setExternalTxHash(chainHash);
        if (transactionRepository.transitionStatusIfCurrent(
                tx.getTxHash(), TransactionStatus.AWAITING_PAYMENT, TransactionStatus.PENDING, now) != 1) {
            throw conflict("Deposit session was already confirmed or expired");
        }
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse withdraw(Long userId, BigDecimal rawAmount, String rawWallet, String idempotencyKey) {
        User user = lockActive(userId);
        idempotency.claim(userId, idempotencyKey, "WITHDRAW");
        BigDecimal amount = money(rawAmount);
        if (amount.compareTo(MIN_WITHDRAW_AMOUNT) < 0) {
            throw bad("Minimum withdrawal amount is $2.50 USDT");
        }
        String wallet = rawWallet == null ? "" : rawWallet.trim();
        if (!TonAddressValidator.isMainnet(wallet)) {
            throw bad("Invalid TON wallet address");
        }
        Instant now = clock.instant();
        boolean openDeposit = transactionRepository
                .findByUserAndStatusOrderByCreatedAtDesc(user, TransactionStatus.AWAITING_PAYMENT).stream()
                .anyMatch(t -> t.getExpiresAt() == null || !now.isAfter(t.getExpiresAt()));
        if (openDeposit) {
            throw conflict("Finish or cancel your active deposit before withdrawing");
        }
        if (!hasApprovedDeposit(user)) {
            throw bad("You must have at least 1 approved deposit on your account before withdrawing");
        }
        if (transactionRepository.existsByUserAndStatus(user, TransactionStatus.PENDING)) {
            throw conflict("You already have a pending transaction awaiting admin review");
        }
        if (user.getBalance().compareTo(amount) < 0) {
            throw bad("Insufficient balance");
        }
        user.setBalance(user.getBalance().subtract(amount));
        userRepository.save(user);
        transactionRepository.save(new BalanceTransaction(
                user, TransactionType.WITHDRAW, amount, TransactionStatus.PENDING, null, wallet));
        return UserResponse.fromEntity(user);
    }

    private boolean hasApprovedDeposit(User user) {
        return transactionRepository.existsByUserAndTypeAndStatus(user, TransactionType.DEPOSIT, TransactionStatus.APPROVED);
    }

    private User lockActive(Long userId) {
        return userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }

    private static BigDecimal money(BigDecimal raw) {
        try {
            return Money.requirePositive(raw);
        } catch (IllegalArgumentException e) {
            throw bad(e.getMessage());
        }
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    private static ResponseStatusException conflict(String m) {
        return new ResponseStatusException(HttpStatus.CONFLICT, m);
    }
}
