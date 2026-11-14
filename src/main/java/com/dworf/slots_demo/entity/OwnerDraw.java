package com.dworf.slots_demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * H15: house profit the owner has taken out of the wallet (the monthly settlement). Append-only. Keeping these
 * rows is what lets reconciliation still balance after house profit is paid out.
 */
@Entity
@Table(name = "owner_draws")
public class OwnerDraw {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(length = 200, updatable = false)
    private String note;

    /** HOUSE = monthly house-profit settlement, POOL = manual correction taken out of the prize pool. */
    @Column(nullable = false, length = 8, updatable = false)
    private String source = "HOUSE";

    @Column(name = "admin_name", nullable = false, length = 64, updatable = false)
    private String adminName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public OwnerDraw() {
    }

    public OwnerDraw(BigDecimal amount, String note, String adminName) {
        this(amount, note, adminName, "HOUSE");
    }

    public OwnerDraw(BigDecimal amount, String note, String adminName, String source) {
        this.source = source;
        this.amount = amount;
        this.note = note;
        this.adminName = adminName;
    }

    public Long getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getSource() { return source; }
    public String getNote() { return note; }
    public String getAdminName() { return adminName; }
    public Instant getCreatedAt() { return createdAt; }
}
