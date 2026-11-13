package com.dworf.slots_demo.service;

import com.dworf.slots_demo.entity.AuditLog;
import com.dworf.slots_demo.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Joins the caller's transaction: an admin action and its audit row commit or roll back together. */
    @Transactional
    public void record(String actor, String action, String target, String detail, String ip) {
        repository.save(new AuditLog(actor, action, target, truncate(detail), ip));
    }

    /** Own transaction: survives a rollback of the caller (use for failed logins / denied actions). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(String actor, String action, String target, String detail, String ip) {
        repository.save(new AuditLog(actor, action, target, truncate(detail), ip));
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 500 ? s.substring(0, 500) : s);
    }
}
