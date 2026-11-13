package com.dworf.slots_demo.service;

import com.dworf.slots_demo.dto.UserResponse;
import com.dworf.slots_demo.entity.TransactionStatus;
import com.dworf.slots_demo.entity.User;
import com.dworf.slots_demo.repository.BalanceTransactionRepository;
import com.dworf.slots_demo.repository.UserRepository;
import com.dworf.slots_demo.security.PasswordPolicy;
import com.dworf.slots_demo.security.UsernamePolicy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;

/** Registration, login, password change and account deletion (C1, C3, C4, C7, H10, H21). */
@Service
public class AccountService {

    /** Result carrying the user plus the freshly issued session, so controllers can set the cookie. */
    public record Authenticated(User user, SessionService.Issued session) {}

    private static final String BAD_CREDENTIALS = "Invalid username or password";

    private final UserRepository userRepository;
    private final BalanceTransactionRepository transactionRepository;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    private final AuthRateLimits limits;
    private final AuditService audit;
    private final Clock clock;
    /** Pre-computed hash used to burn the same CPU time when the username does not exist (no timing oracle). */
    private final String dummyHash;

    public AccountService(UserRepository userRepository, BalanceTransactionRepository transactionRepository,
                          PasswordEncoder encoder, SessionService sessions, AuthRateLimits limits,
                          AuditService audit, Clock clock) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.encoder = encoder;
        this.sessions = sessions;
        this.limits = limits;
        this.audit = audit;
        this.clock = clock;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public Authenticated register(String rawUsername, String password, String ip) {
        if (!limits.registerPerIp.tryAcquire(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many registrations, try later");
        }
        String username = UsernamePolicy.validate(rawUsername);
        PasswordPolicy.validate(password, username);
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
        try {
            User saved = userRepository.saveAndFlush(new User(username, encoder.encode(password)));
            return new Authenticated(saved, sessions.issuePlayer(saved.getId()));
        } catch (DataIntegrityViolationException race) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
    }

    @Transactional
    public Authenticated login(String rawUsername, String password, String ip) {
        String username;
        try {
            username = UsernamePolicy.normalize(rawUsername);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        if (!limits.loginPerIp.tryAcquire(ip) || !limits.loginPerUserAndIp.tryAcquire(username + "|" + ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, try again later");
        }
        User user = userRepository.findByUsernameAndDeletedAtIsNull(username).orElse(null);
        boolean ok = verify(user, password);
        if (!ok) {
            audit.recordIndependent(username, "LOGIN_FAILED", null, null, ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        limits.loginPerUserAndIp.reset(username + "|" + ip);
        return new Authenticated(user, sessions.issuePlayer(user.getId()));
    }

    /** Changes the password, revokes every existing session and returns a new one (H9/H22). */
    @Transactional
    public Authenticated changePassword(Long userId, String currentPassword, String newPassword) {
        User user = lockActive(userId);
        requirePasswordWithLimit(user, currentPassword);
        PasswordPolicy.validate(newPassword, user.getUsername());
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different from the current password");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        userRepository.save(user);
        sessions.revokeAllForUser(userId);
        audit.record(user.getUsername(), "PASSWORD_CHANGED", user.getUsername(), null, null);
        return new Authenticated(user, sessions.issuePlayer(userId));
    }

    /**
     * C7: soft delete. Refused while money or obligations remain, so history stays intact and
     * the reconciliation invariant is never broken by an account disappearing.
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = lockActive(userId);
        requirePasswordWithLimit(user, password);
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

    private void requirePasswordWithLimit(User user, String password) {
        if (!limits.passwordCheckPerUser.tryAcquire(user.getUsername())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, try again later");
        }
        if (!verify(user, password)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Wrong password");
        }
        limits.passwordCheckPerUser.reset(user.getUsername());
    }

    /** Always performs one BCrypt comparison, whether or not the user exists. Blank passwords never match. */
    private boolean verify(User user, String password) {
        String candidate = password == null ? "" : password;
        boolean hasHash = user != null && user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
        boolean matches = encoder.matches(candidate, hasHash ? user.getPasswordHash() : dummyHash);
        return hasHash && matches && !candidate.isBlank();
    }
}
