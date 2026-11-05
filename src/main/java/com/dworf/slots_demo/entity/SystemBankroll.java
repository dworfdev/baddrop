package com.dworf.slots_demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Single-row (id = 1) persistent platform bankroll backing the Split-Inflow Bankroll Model.
 * <ul>
 *     <li>{@code prizePool}   - cumulative shared treasury that funds profitable/break-even items</li>
 *     <li>{@code houseProfit} - unspendable platform net revenue (7% of all crate fees)</li>
 * </ul>
 * Note: The 33% Floor Buffer is ephemeral and in-memory; it exists strictly per active spin
 * and does NOT accumulate in the database.
 * Every write operation must run under a pessimistic write lock (SELECT ... FOR UPDATE).
 */
@Entity
@Table(name = "system_bankroll")
public class SystemBankroll {

    public static final long SINGLETON_ID = 1L;
    private static final int MONEY_SCALE = 4;

    @Id
    @ColumnDefault("1")
    private Long id = SINGLETON_ID;

    @Column(name = "prize_pool", nullable = false, precision = 19, scale = 4)
    private BigDecimal prizePool = zero();

    @Column(name = "house_profit", nullable = false, precision = 19, scale = 4)
    @ColumnDefault("0.0000")
    private BigDecimal houseProfit = zero();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SystemBankroll() {
    }

    public SystemBankroll(BigDecimal initialPrizePool) {
        this.prizePool = initialPrizePool.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getPrizePool() {
        return prizePool;
    }

    public void setPrizePool(BigDecimal prizePool) {
        this.prizePool = prizePool;
    }

    public BigDecimal getHouseProfit() {
        return houseProfit;
    }

    public void setHouseProfit(BigDecimal houseProfit) {
        this.houseProfit = houseProfit;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
