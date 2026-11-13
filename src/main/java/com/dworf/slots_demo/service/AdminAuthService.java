package com.dworf.slots_demo.service;

import com.dworf.slots_demo.security.Totp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

/**
 * C6: admin login = username + BCrypt password + TOTP. Fails closed when not configured.
 * Configuration (env vars, never committed):
 *   slots.admin.username, slots.admin.password-hash (BCrypt), slots.admin.totp-secret (base32).
 */
@Service
public class AdminAuthService {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);
    private static final String BAD = "Invalid credentials";

    private final String username;
    private final String passwordHash;
    private final String totpSecret;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    private final AuthRateLimits limits;
    private final AuditService audit;
    private final Clock clock;
    private final String dummyHash;
    private final boolean replayProtection;
    /** TOTP step that was last accepted: a code can be used once (replay protection). */
    private final AtomicLong lastUsedStep = new AtomicLong(-1);

    public AdminAuthService(
            @Value("${slots.admin.username:}") String username,
            @Value("${slots.admin.password-hash:}") String passwordHash,
            @Value("${slots.admin.totp-secret:}") String totpSecret,
            @Value("${slots.admin.totp-replay-protection:true}") boolean replayProtection,
            PasswordEncoder encoder, SessionService sessions, AuthRateLimits limits,
            AuditService audit, Clock clock) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.totpSecret = totpSecret;
        this.replayProtection = replayProtection;
        this.encoder = encoder;
        this.sessions = sessions;
        this.limits = limits;
        this.audit = audit;
        this.clock = clock;
        this.dummyHash = encoder.encode("dummy-admin-password");
        if (!isConfigured()) {
            log.warn("Admin login is DISABLED: slots.admin.username / password-hash / totp-secret are not all set");
        }
    }

    public boolean isConfigured() {
        return !username.isBlank() && !passwordHash.isBlank() && !totpSecret.isBlank();
    }

    public SessionService.Issued login(String user, String password, String totp, String ip) {
        if (!limits.adminLoginPerIp.tryAcquire(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, try again later");
        }
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, BAD);
        }
        // Evaluate every factor unconditionally so timing does not reveal which one failed.
        boolean userOk = MessageDigest.isEqual(
                username.getBytes(StandardCharsets.UTF_8),
                (user == null ? "" : user).getBytes(StandardCharsets.UTF_8));
        boolean pwOk = encoder.matches(password == null ? "" : password, userOk ? passwordHash : dummyHash);
        long now = clock.millis() / 1000;
        boolean totpOk = Totp.verify(totpSecret, totp, now, 1);
        long step = now / 30;
        boolean fresh = totpOk && (!replayProtection || claimStep(step));
        if (!(userOk && pwOk && fresh)) {
            audit.recordIndependent(user == null ? "?" : abbreviate(user), "ADMIN_LOGIN_FAILED", null, null, ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, BAD);
        }
        audit.recordIndependent(username, "ADMIN_LOGIN", null, null, ip);
        return sessions.issueAdmin(username);
    }

    private boolean claimStep(long step) {
        while (true) {
            long prev = lastUsedStep.get();
            if (step <= prev) return false;
            if (lastUsedStep.compareAndSet(prev, step)) return true;
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 32 ? s.substring(0, 32) : s;
    }
}
