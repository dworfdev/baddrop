package com.dworf.slots_demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** H19: a (user, key) pair may be claimed once; the claim is rolled back with the money operation. */
@Entity
@Table(name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(name = "uq_idem_user_key", columnNames = {"user_id", "key_value"}))
public class IdempotencyKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "key_value", nullable = false, length = 64)
    private String keyValue;

    @Column(nullable = false, length = 32)
    private String operation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected IdempotencyKey() {
    }

    public IdempotencyKey(Long userId, String keyValue, String operation) {
        this.userId = userId;
        this.keyValue = keyValue;
        this.operation = operation;
    }
}
