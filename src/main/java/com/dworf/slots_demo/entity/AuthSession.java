package com.dworf.slots_demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** C4/H9: server-side opaque session. Only the SHA-256 of the token is stored. */
@Entity
@Table(name = "auth_sessions", indexes = {@Index(name = "idx_auth_sessions_user", columnList = "user_id")})
public class AuthSession {

    public enum Role { PLAYER, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Role role;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "admin_username", length = 64)
    private String adminUsername;

    @Column(name = "csrf_token", nullable = false, length = 64, updatable = false)
    private String csrfToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AuthSession() {
    }

    public AuthSession(String tokenHash, Role role, Long userId, String adminUsername, String csrfToken, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.role = role;
        this.userId = userId;
        this.adminUsername = adminUsername;
        this.csrfToken = csrfToken;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Role getRole() { return role; }
    public Long getUserId() { return userId; }
    public String getAdminUsername() { return adminUsername; }
    public String getCsrfToken() { return csrfToken; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void revoke(Instant at) { this.revokedAt = at; }
    public boolean isActive(Instant now) { return revokedAt == null && now.isBefore(expiresAt); }
}
