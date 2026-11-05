package com.dworf.slots_demo.service;

import com.dworf.slots_demo.dto.AdminUserOverviewResponse;
import com.dworf.slots_demo.dto.BalanceOperationRequest;
import com.dworf.slots_demo.dto.BalanceResponse;
import com.dworf.slots_demo.dto.BankrollResponse;
import com.dworf.slots_demo.dto.ChangePasswordRequest;
import com.dworf.slots_demo.dto.ConfirmDepositRequest;
import com.dworf.slots_demo.dto.DepositSessionResponse;
import com.dworf.slots_demo.dto.ItemOperationRequest;
import com.dworf.slots_demo.dto.ItemsResponse;
import com.dworf.slots_demo.dto.OpenCaseResponse;
import com.dworf.slots_demo.dto.TransactionResponse;
import com.dworf.slots_demo.dto.UserCredentialsRequest;
import com.dworf.slots_demo.dto.UserResponse;
import com.dworf.slots_demo.entity.BalanceTransaction;
import com.dworf.slots_demo.entity.ItemType;
import com.dworf.slots_demo.entity.TransactionStatus;
import com.dworf.slots_demo.entity.TransactionType;
import com.dworf.slots_demo.entity.User;
import com.dworf.slots_demo.repository.BalanceTransactionRepository;
import com.dworf.slots_demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    public static final long DEPOSIT_TIMER_SECONDS = 15 * 60L;
    public static final BigDecimal MIN_DEPOSIT_AMOUNT = new BigDecimal("5.00");
    public static final BigDecimal MIN_WITHDRAW_AMOUNT = new BigDecimal("10.00");

    private final UserRepository userRepository;
    private final BalanceTransactionRepository transactionRepository;
    private final CaseOpenerEngine caseOpenerEngine;
    private final CaseOpeningService caseOpeningService;
    private final String adminPassword;

    public UserService(
            UserRepository userRepository,
            BalanceTransactionRepository transactionRepository,
            CaseOpenerEngine caseOpenerEngine,
            CaseOpeningService caseOpeningService,
            @Value("${slots.admin.password:admin1234}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.caseOpenerEngine = caseOpenerEngine;
        this.caseOpeningService = caseOpeningService;
        this.adminPassword = adminPassword;
    }

    @Transactional
    public UserResponse createUser(UserCredentialsRequest request) {
        validateCredentialsInput(request.username(), request.password());

        if (userRepository.existsByUsername(request.username().trim())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User with username '" + request.username().trim() + "' already exists"
            );
        }

        User user = new User(request.username().trim(), request.password());
        User saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse loginUser(UserCredentialsRequest request) {
        User user = authenticateUser(request.username(), request.password());
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse changePassword(ChangePasswordRequest request) {
        User user = authenticateUser(request.username(), request.password());
        if (request.newPassword() == null || request.newPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must not be blank");
        }
        if (request.newPassword().equals(user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different from the current password");
        }

        user.setPassword(request.newPassword());
        return UserResponse.fromEntity(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Map<String, String> logoutUser(UserCredentialsRequest request) {
        User user = authenticateUser(request.username(), request.password());
        return Map.of(
                "username", user.getUsername(),
                "status", "LOGGED_OUT",
                "message", "User '" + user.getUsername() + "' logged out successfully"
        );
    }

    @Transactional
    public Map<String, String> deleteAccount(UserCredentialsRequest request) {
        User user = authenticateUser(request.username(), request.password());
        String username = user.getUsername();
        transactionRepository.deleteByUser(user);
        userRepository.delete(user);
        return Map.of(
                "username", username,
                "status", "DELETED",
                "message", "Account '" + username + "' has been deleted"
        );
    }

    /**
     * Step 1 -> Step 2 Deposit Session Start:
     * Acquires a pessimistic lock on the user, rejects if the user already has a PENDING transaction
     * awaiting admin review, expires any old AWAITING_PAYMENT sessions, and starts a new 15-minute
     * deposit session with a unique cryptographic txHash.
     */
    @Transactional
    public DepositSessionResponse startDepositSession(BalanceOperationRequest request) {
        User user = authenticateAndLockUser(request.username(), request.password());
        validatePositiveAmount(request.amount());
        if (request.amount().compareTo(MIN_DEPOSIT_AMOUNT) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Minimum deposit amount is $5.00 USDT"
            );
        }

        if (transactionRepository.existsByUserAndStatus(user, TransactionStatus.PENDING)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already have a pending transaction awaiting admin review"
            );
        }

        List<BalanceTransaction> existingAwaiting = transactionRepository
                .findByUserAndStatusOrderByCreatedAtDesc(user, TransactionStatus.AWAITING_PAYMENT);
        for (BalanceTransaction oldTx : existingAwaiting) {
            oldTx.setStatus(TransactionStatus.EXPIRED);
            transactionRepository.save(oldTx);
        }

        Instant expiresAt = Instant.now().plusSeconds(DEPOSIT_TIMER_SECONDS);
        BalanceTransaction tx = new BalanceTransaction(
                user,
                TransactionType.DEPOSIT,
                request.amount(),
                TransactionStatus.AWAITING_PAYMENT,
                expiresAt,
                DepositSessionResponse.DEFAULT_ADDRESS
        );
        BalanceTransaction saved = transactionRepository.save(tx);
        boolean hasApprovedDeposit = transactionRepository.existsByUserAndTypeAndStatus(
                user,
                TransactionType.DEPOSIT,
                TransactionStatus.APPROVED
        );
        return DepositSessionResponse.fromEntity(saved, false, hasApprovedDeposit);
    }

    /**
     * Checks if the user has an active 15-minute deposit session in progress (Step 2),
     * whether the user currently has a PENDING transaction awaiting admin review,
     * and whether the user has at least 1 APPROVED deposit on their account.
     */
    @Transactional
    public DepositSessionResponse getActiveDepositSession(String username, String password) {
        User user = authenticateOrFindUser(username, password);
        boolean hasPending = transactionRepository.existsByUserAndStatus(user, TransactionStatus.PENDING);
        boolean hasApprovedDeposit = transactionRepository.existsByUserAndTypeAndStatus(
                user,
                TransactionType.DEPOSIT,
                TransactionStatus.APPROVED
        );

        List<BalanceTransaction> awaitingList = transactionRepository
                .findByUserAndStatusOrderByCreatedAtDesc(user, TransactionStatus.AWAITING_PAYMENT);

        Instant now = Instant.now();
        BalanceTransaction activeTx = null;
        for (BalanceTransaction tx : awaitingList) {
            if (tx.getExpiresAt() != null && now.isAfter(tx.getExpiresAt())) {
                tx.setStatus(TransactionStatus.EXPIRED);
                transactionRepository.save(tx);
            } else if (activeTx == null) {
                activeTx = tx;
            } else {
                tx.setStatus(TransactionStatus.EXPIRED);
                transactionRepository.save(tx);
            }
        }

        if (activeTx == null) {
            return DepositSessionResponse.inactive(user.getUsername(), hasPending, hasApprovedDeposit);
        }
        return DepositSessionResponse.fromEntity(activeTx, hasPending, hasApprovedDeposit);
    }

    public UserResponse confirmDepositSent(UserCredentialsRequest request) {
        return confirmDepositSent(new ConfirmDepositRequest(request.username(), request.password(), null));
    }

    /**
     * Step 2 -> Step 3 ("Sent" button clicked):
     * Protected by pessimistic write locks + atomic conditional SQL status transition so that
     * concurrent requests cannot duplicate or double-confirm the same deposit session.
     */
    @Transactional
    public UserResponse confirmDepositSent(ConfirmDepositRequest request) {
        User user = authenticateAndLockUser(request.username(), request.password());

        if (transactionRepository.existsByUserAndStatus(user, TransactionStatus.PENDING)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already have a pending transaction awaiting admin review"
            );
        }

        List<BalanceTransaction> awaitingList = transactionRepository
                .findByUserAndStatusOrderByCreatedAtDesc(user, TransactionStatus.AWAITING_PAYMENT);

        if (awaitingList.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No active deposit session found. Please start a new deposit."
            );
        }

        BalanceTransaction tx = awaitingList.get(0);
        if (request.txHash() != null && !request.txHash().isBlank() && !tx.getTxHash().equals(request.txHash().trim())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transaction hash mismatch or session superseded"
            );
        }

        Instant now = Instant.now();
        if (tx.getExpiresAt() != null && now.isAfter(tx.getExpiresAt())) {
            tx.setStatus(TransactionStatus.EXPIRED);
            transactionRepository.save(tx);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Deposit 15-minute timer has expired. Please start a new deposit."
            );
        }

        int updatedRows = transactionRepository.transitionStatusIfCurrent(
                tx.getTxHash(),
                TransactionStatus.AWAITING_PAYMENT,
                TransactionStatus.PENDING,
                now
        );
        if (updatedRows != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Deposit session was already confirmed or expired"
            );
        }

        return UserResponse.fromEntity(user);
    }

    /**
     * Player Withdraw Request:
     * Protected by pessimistic write lock on User. Rejects if the user already has a PENDING
     * transaction awaiting admin review. Subtracts amount immediately and creates a PENDING
     * WITHDRAW transaction with a unique cryptographic txHash.
     */
    @Transactional
    public UserResponse withdraw(BalanceOperationRequest request) {
        User user = authenticateAndLockUser(request.username(), request.password());
        validatePositiveAmount(request.amount());

        if (request.amount().compareTo(MIN_WITHDRAW_AMOUNT) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Minimum withdrawal amount is $10.00 USDT"
            );
        }

        if (!transactionRepository.existsByUserAndTypeAndStatus(user, TransactionType.DEPOSIT, TransactionStatus.APPROVED)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You must have at least 1 approved deposit on your account before withdrawing"
            );
        }

        if (transactionRepository.existsByUserAndStatus(user, TransactionStatus.PENDING)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already have a pending transaction awaiting admin review"
            );
        }

        if (request.walletAddress() == null || request.walletAddress().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "TON wallet address must not be empty"
            );
        }

        if (user.getBalance().compareTo(request.amount()) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient balance. Current balance: " + user.getBalance() + ", requested withdrawal: " + request.amount()
            );
        }

        user.setBalance(user.getBalance().subtract(request.amount()));
        User savedUser = userRepository.save(user);

        String walletAddr = request.walletAddress().trim();
        BalanceTransaction tx = new BalanceTransaction(
                savedUser,
                TransactionType.WITHDRAW,
                request.amount(),
                TransactionStatus.PENDING,
                null,
                walletAddr
        );
        transactionRepository.save(tx);

        return UserResponse.fromEntity(savedUser);
    }

    /**
     * Admin approves a PENDING transaction identified by its unique txHash:
     * - DEPOSIT: adds amount to user's balance now.
     * - WITHDRAW: amount was already subtracted on request, just marks APPROVED.
     */
    @Transactional
    public TransactionResponse approveTransaction(String txHash) {
        BalanceTransaction tx = getPendingTransactionByHashOrThrow(txHash);
        User user = findUserByUsernameForUpdate(tx.getUser().getUsername());

        if (tx.getType() == TransactionType.DEPOSIT) {
            user.setBalance(user.getBalance().add(tx.getAmount()));
            userRepository.save(user);
        }

        tx.setStatus(TransactionStatus.APPROVED);
        BalanceTransaction saved = transactionRepository.save(tx);
        return TransactionResponse.fromEntity(saved);
    }

    /**
     * Admin rejects/declines a PENDING transaction identified by its unique txHash:
     * - DEPOSIT: nothing was added, just marks REJECTED.
     * - WITHDRAW: refunds the subtracted amount back to the user's balance.
     */
    @Transactional
    public TransactionResponse rejectTransaction(String txHash) {
        BalanceTransaction tx = getPendingTransactionByHashOrThrow(txHash);
        User user = findUserByUsernameForUpdate(tx.getUser().getUsername());

        if (tx.getType() == TransactionType.WITHDRAW) {
            user.setBalance(user.getBalance().add(tx.getAmount()));
            userRepository.save(user);
        }

        tx.setStatus(TransactionStatus.REJECTED);
        BalanceTransaction saved = transactionRepository.save(tx);
        return TransactionResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllPendingTransactions() {
        return transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PENDING)
                .stream()
                .map(TransactionResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getUserPendingTransactions(String username) {
        User user = findUserByUsername(username);
        return transactionRepository.findByUserAndStatusOrderByCreatedAtDesc(user, TransactionStatus.PENDING)
                .stream()
                .map(TransactionResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserOverviewResponse getAdminUserOverview(String username) {
        User user = findUserByUsername(username);
        List<TransactionResponse> pending = transactionRepository
                .findByUserAndStatusOrderByCreatedAtDesc(user, TransactionStatus.PENDING)
                .stream()
                .map(TransactionResponse::fromEntity)
                .toList();
        List<TransactionResponse> history = transactionRepository
                .findByUserAndStatusOrderByCreatedAtAsc(user, TransactionStatus.APPROVED)
                .stream()
                .map(TransactionResponse::fromEntity)
                .toList();
        return AdminUserOverviewResponse.from(user, pending, history);
    }

    @Transactional
    public UserResponse buyItem(ItemOperationRequest request) {
        User user = authenticateOrFindUser(request.username(), request.password());
        if (request.item() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item must be provided (IRON_CRATE, SILVER_CRATE, or GOLD_CRATE)");
        }

        BigDecimal price = request.item().getPrice();
        if (user.getBalance().compareTo(price) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient funds to buy " + request.item() + " ($" + price + "). Current balance: $" + user.getBalance()
            );
        }

        user.setBalance(user.getBalance().subtract(price));
        user.getItems().add(request.item());
        return UserResponse.fromEntity(userRepository.save(user));
    }

    /**
     * Opens one instance of the requested case from the player's inventory using the
     * Low-Variance CDF Mathematical Engine (CaseOpenerEngine) and immediately credits
     * the won USDT amount to the player's balance.
     */
    @Transactional
    public OpenCaseResponse openCase(ItemOperationRequest request) {
        User user = authenticateOrFindUser(request.username(), request.password());
        if (request.item() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case item must be provided");
        }

        boolean removed = user.getItems().remove(request.item());
        if (!removed) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You do not own any " + request.item() + " in your inventory"
            );
        }

        CaseOpenerEngine.Outcome outcome = caseOpeningService.open(request.item()).outcome();
        CaseOpenerEngine.CaseMathStats stats = caseOpenerEngine.calculateStats(request.item());
        boolean isJackpot = caseOpenerEngine.isMaxWin(request.item(), outcome.winAmount());

        user.setBalance(user.getBalance().add(outcome.winAmount()));
        User savedUser = userRepository.save(user);

        return new OpenCaseResponse(
                savedUser.getUsername(),
                request.item(),
                request.item().getPrice(),
                outcome.winAmount(),
                isJackpot,
                outcome.tierLabel(),
                savedUser.getBalance(),
                List.copyOf(savedUser.getItems()),
                stats.expectedValue(),
                stats.rtpPercent(),
                stats.variance()
        );
    }

    public List<CaseOpenerEngine.CaseMathStats> getAllCasesMathStats() {
        return Arrays.stream(ItemType.values())
                .map(caseOpenerEngine::calculateStats)
                .toList();
    }

    public BankrollResponse getBankroll() {
        return caseOpeningService.getBankroll();
    }

    public Map<String, Object> verifyPromoCode(String promo) {
        if (promo != null && adminPassword != null && !adminPassword.isBlank() && adminPassword.trim().equals(promo.trim())) {
            return Map.of(
                    "valid", true,
                    "redirectUrl", "/admin.html",
                    "message", "Admin access granted"
            );
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promo is not found");
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getTopRichUsers() {
        return userRepository.findTop3ByOrderByBalanceDesc()
                .stream()
                .map(UserResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(String username, String password) {
        User user = authenticateOrFindUser(username, password);
        return UserResponse.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getUserBalance(String username, String password) {
        User user = authenticateOrFindUser(username, password);
        return new BalanceResponse(user.getUsername(), user.getBalance());
    }

    @Transactional(readOnly = true)
    public ItemsResponse getUserItems(String username, String password) {
        User user = authenticateOrFindUser(username, password);
        return new ItemsResponse(user.getUsername(), List.copyOf(user.getItems()));
    }

    private BalanceTransaction getPendingTransactionByHashOrThrow(String txHash) {
        if (txHash == null || txHash.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction hash must be provided");
        }
        BalanceTransaction tx = transactionRepository.findByTxHashForUpdate(txHash.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Transaction not found"
                ));
        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transaction is already " + tx.getStatus()
            );
        }
        return tx;
    }

    private User findUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must not be blank");
        }
        return userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User '" + username.trim() + "' not found"
                ));
    }

    private User findUserByUsernameForUpdate(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must not be blank");
        }
        return userRepository.findByUsernameForUpdate(username.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User '" + username.trim() + "' not found"
                ));
    }

    private User authenticateAndLockUser(String username, String password) {
        User user = findUserByUsernameForUpdate(username);
        if (password != null && !password.isBlank()) {
            if (!user.getPassword().equals(password)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password for user '" + username.trim() + "'");
            }
        }
        return user;
    }

    /**
     * Authenticates user with password when password is provided, or looks up by username for admin operations.
     */
    private User authenticateOrFindUser(String username, String password) {
        User user = findUserByUsername(username);
        if (password != null && !password.isBlank()) {
            if (!user.getPassword().equals(password)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password for user '" + username.trim() + "'");
            }
        }
        return user;
    }

    private User authenticateUser(String username, String password) {
        validateCredentialsInput(username, password);
        User user = findUserByUsername(username);

        if (!user.getPassword().equals(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password for user '" + username.trim() + "'");
        }

        return user;
    }

    private void validateCredentialsInput(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must not be blank");
        }
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not be blank");
        }
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than 0");
        }
    }
}
