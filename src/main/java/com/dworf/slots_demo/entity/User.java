package com.dworf.slots_demo.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * H22: players have no password at all. Identity is the Telegram account id (verified from the signed Mini App
 * initData), so there is nothing to guess, leak or reset.
 * C5: optimistic @Version as a second line of defence behind the pessimistic locks.
 * C7: soft delete (deletedAt) so financial history is never destroyed.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    /** Telegram user id. Never changes, unique per account. Dev-login accounts get a synthetic negative id. */
    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Version
    private Long version;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_items", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "item_slug", nullable = false, length = 64)
    private List<String> items = new ArrayList<>();

    public User() {
    }

    public User(String username, long telegramId) {
        this.username = username;
        this.telegramId = telegramId;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public Long getTelegramId() { return telegramId; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public List<String> getItems() { return items; }
    public void setItems(List<String> items) { this.items = items; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void markDeleted(Instant at) { this.deletedAt = at; }
    /** A deleted account whose owner logs in again comes back empty (deletion required a zero balance and no items). */
    public void reactivate() { this.deletedAt = null; }
    public boolean isDeleted() { return deletedAt != null; }
}
