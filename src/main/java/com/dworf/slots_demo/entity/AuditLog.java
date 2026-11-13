package com.dworf.slots_demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** H14/C6: append-only audit trail for every privileged action. No setters, no update/delete paths in code. */
@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant at = Instant.now();

    @Column(nullable = false, updatable = false, length = 64)
    private String actor;

    @Column(nullable = false, updatable = false, length = 64)
    private String action;

    @Column(updatable = false, length = 128)
    private String target;

    @Column(updatable = false, length = 512)
    private String detail;

    @Column(updatable = false, length = 64)
    private String ip;

    protected AuditLog() {
    }

    public AuditLog(String actor, String action, String target, String detail, String ip) {
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.detail = detail;
        this.ip = ip;
    }

    public Long getId() { return id; }
    public Instant getAt() { return at; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getDetail() { return detail; }
}
