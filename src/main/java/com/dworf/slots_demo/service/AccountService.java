package com.dworf.slots_demo.service;

import com.dworf.slots_demo.dto.UserResponse;
import com.dworf.slots_demo.entity.TransactionStatus;
import com.dworf.slots_demo.entity.User;
import com.dworf.slots_demo.repository.BalanceTransactionRepository;
import com.dworf.slots_demo.repository.UserRepository;
import com.dworf.slots_demo.security.TelegramInitData;
import com.dworf.slots_demo.security.TelegramUsernames;
import com.dworf.slots_demo.security.UsernamePolicy;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;

/**
 * Telegram login (find-or-create by Telegram id), account deletion and the "who am I" read.
 * Players have no password: the Mini App's signed initData is the only credential (H22, C1, C4, C7).
 */
@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final int CREATE_ATTEMPTS = 5;

    /** Result carrying the user plus the freshly issued session, so controllers can set the cookie. */
    public record Authenticated(User user, SessionService.Issued session) {}

    private final UserRepository userRepository;
    private final BalanceTransactionRepository transactionRepository;
    private final SessionService sessions;
    private final AuthRateLimits limits;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final String botToken;
    private final long maxAuthAgeSeconds;
    private final boolean devLoginEnabled;

    public AccountService(UserRepository userRepository, BalanceTransactionRepository transactionRepository,
                          SessionService sessions, AuthRateLimits limits, AuditService audit, Clock clock,
                          PlatformTransactionManager txManager,
                          @Value("${slots.telegram.bot-token:}") String botToken,
                          @Value("${slots.telegram.max-auth-age-seconds:3600}") long maxAuthAgeSeconds,
                          @Value("${slots.auth.dev-login-enabled:false}") boolean devLoginEnabled) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.sessions = sessions;
        this.limits = limits;
        this.audit = audit;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.botToken = botToken == null ? "" : botToken.trim();
        this.maxAuthAgeSeconds = maxAuthAgeSeconds;
        this.devLoginEnabled = devLoginEnabled;
    }

    @PostConstruct
    void warnAboutConfiguration() {
        if (botToken.isEmpty()) {
            log.warn("Telegram login is DISABLED: slots.telegram.bot-token (TELEGRAM_BOT_TOKEN) is not set");
        }
        if (devLoginEnabled) {
            log.warn("DEV LOGIN IS ENABLED (slots.auth.dev-login-enabled): anyone can log in as any username. Never use this in production");
        }
    }

    public boolean isDevLoginEnabled() {
        return devLoginEnabled;
    }

    /** Verifies the signed Mini App data, then finds or creates the account for that Telegram id. */
    public Authenticated loginWithTelegram(String initData, String ip) {
        if (botToken.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram login is not configured");
        }
        if (!limits.loginPerIp.tryAcquire(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, try again later");
        }
        TelegramInitData.Verified who;
        try {
            who = TelegramInitData.verify(initData, botToken, clock.instant().getEpochSecond(), maxAuthAgeSeconds);
        } catch (IllegalArgumentException invalid) {
            audit.recordIndependent("telegram", "LOGIN_FAILED", null, invalid.getMessage(), ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Telegram sign-in failed. Reopen the app from Telegram");
        }
        User user = findOrCreate(who.userId(), who.username(), who.firstName());
        return new Authenticated(user, sessions.issuePlayer(user.getId()));
    }

    /** Test/dev convenience, refused unless explicitly enabled. Creates the player on first use. */
    public Authenticated devLogin(String rawUsername, String ip) {
        if (!devLoginEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (!limits.loginPerIp.tryAcquire(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, try again later");
        }
        String username = UsernamePolicy.validate(rawUsername);
        User existing = userRepository.findByUsernameAndDeletedAtIsNull(username).orElse(null);
        long syntheticId = existing != null ? existing.getTelegramId() : -(Math.abs((long) username.hashCode()) + 1);
        User user = findOrCreate(syntheticId, username, null);
        return new Authenticated(user, sessions.issuePlayer(user.getId()));
    }

    /**
     * Two simultaneous first logins for the same Telegram id (or two accounts racing for one username) end in a
     * unique-key violation inside the transaction; each attempt therefore runs in its own transaction and a
     * loser simply tries again and finds the winner's row.
     */
    private User findOrCreate(long telegramId, String telegramUsername, String firstName) {
        for (int attempt = 0; attempt < CREATE_ATTEMPTS; attempt++) {
            final int n = attempt;
            try {
                User u = tx.execute(status -> findOrCreateOnce(telegramId, telegramUsername, firstName, n));
                if (u != null) return u;
            } catch (DataIntegrityViolationException race) {
                log.debug("Account creation race for telegram id {}, retrying", telegramId);
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not create the account, please retry");
    }

    private User findOrCreateOnce(long telegramId, String telegramUsername, String firstName, int attempt) {
        User existing = userRepository.findByTelegramId(telegramId).orElse(null);
        if (existing != null) {
            if (existing.isDeleted()) {
                existing.reactivate();
                userRepository.saveAndFlush(existing);
                audit.record(existing.getUsername(), "ACCOUNT_REACTIVATED", existing.getUsername(), null, null);
            }
            return existing;
        }
        String base = TelegramUsernames.derive(telegramUsername, firstName, telegramId);
        String username = base;
        if (attempt > 0 || userRepository.existsByUsername(base)) {
            username = TelegramUsernames.withSuffix(base, telegramId, attempt);
        }
        User created = userRepository.saveAndFlush(new User(username, telegramId));
        audit.record(username, "ACCOUNT_CREATED", username, null, null);
        return created;
    }

    /**
     * C7: soft delete. Refused while money or obligations remain, so history stays intact and
     * the reconciliation invariant is never broken by an account disappearing.
     */
    @Transactional
    public void deleteAccount(Long userId, String confirm) {
        User user = lockActive(userId);
        if (confirm == null || !confirm.trim().equalsIgnoreCase(user.getUsername())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmation does not match the account name");
        }
        if (user.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Withdraw or spend your balance before deleting the account");
        }
        if (!user.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Open or use your cases before deleting the account");
        }
        if (transactionRepository.existsByUserAndStatus(user, TransactionStatus.PENDING)
                || transactionRepository.existsByUserAndStatus(user, TransactionStatus.AWAITING_PAYMENT)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have an unfinished transaction");
        }
        user.markDeleted(clock.instant());
        userRepository.save(user);
        sessions.revokeAllForUser(userId);
        audit.record(user.getUsername(), "ACCOUNT_DELETED", user.getUsername(), null, null);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return UserResponse.fromEntity(userRepository.findActiveById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")));
    }

    private User lockActive(Long userId) {
        return userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }
}
