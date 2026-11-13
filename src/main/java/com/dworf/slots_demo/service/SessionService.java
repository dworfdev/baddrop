package com.dworf.slots_demo.service;

import com.dworf.slots_demo.entity.AuthSession;
import com.dworf.slots_demo.repository.AuthSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** C4/H9: opaque, server-side, revocable sessions. The raw token only ever lives in the client cookie. */
@Service
public class SessionService {

    public record Issued(String rawToken, AuthSession session) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthSessionRepository repository;
    private final Clock clock;
    private final Duration playerTtl;
    private final Duration adminTtl;

    public SessionService(
            AuthSessionRepository repository,
            Clock clock,
            @Value("${slots.session.player-ttl-hours:12}") long playerHours,
            @Value("${slots.session.admin-ttl-minutes:60}") long adminMinutes
    ) {
        this.repository = repository;
        this.clock = clock;
        this.playerTtl = Duration.ofHours(playerHours);
        this.adminTtl = Duration.ofMinutes(adminMinutes);
    }

    @Transactional
    public Issued issuePlayer(Long userId) {
        return issue(AuthSession.Role.PLAYER, userId, null, playerTtl);
    }

    @Transactional
    public Issued issueAdmin(String adminUsername) {
        return issue(AuthSession.Role.ADMIN, null, adminUsername, adminTtl);
    }

    private Issued issue(AuthSession.Role role, Long userId, String adminUsername, Duration ttl) {
        String raw = randomToken(32);
        String csrf = randomToken(24);
        AuthSession s = new AuthSession(sha256Hex(raw), role, userId, adminUsername, csrf, clock.instant().plus(ttl));
        return new Issued(raw, repository.save(s));
    }

    @Transactional(readOnly = true)
    public Optional<AuthSession> resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 128) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return repository.findByTokenHash(sha256Hex(rawToken)).filter(s -> s.isActive(now));
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        repository.findByTokenHash(sha256Hex(rawToken)).ifPresent(s -> s.revoke(clock.instant()));
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void purgeExpired() {
        repository.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(7)));
    }

    static String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
