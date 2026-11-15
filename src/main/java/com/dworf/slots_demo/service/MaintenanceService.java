package com.dworf.slots_demo.service;

import com.dworf.slots_demo.entity.PlatformSetting;
import com.dworf.slots_demo.repository.PlatformSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Emergency switch. While enabled, every player-facing API call answers 503 "maintenance" (see AuthInterceptor)
 * and the frontend shows a "technical works" screen. Admin APIs stay reachable so the switch can be turned off again.
 * The flag lives in the database, so it survives a restart; reads are served from memory.
 */
@Service
public class MaintenanceService {

    static final String KEY = "maintenance";

    private final PlatformSettingRepository settings;
    private final AuditService audit;
    private volatile Boolean cached;

    public MaintenanceService(PlatformSettingRepository settings, AuditService audit) {
        this.settings = settings;
        this.audit = audit;
    }

    public boolean isEnabled() {
        Boolean c = cached;
        if (c != null) return c;
        try {
            boolean v = settings.findById(KEY).map(s -> "true".equals(s.getValue())).orElse(false);
            cached = v;
            return v;
        } catch (RuntimeException e) {
            return false; // database trouble: do not cache, the next call retries
        }
    }

    @Transactional
    public boolean set(boolean enabled, String actor, String ip) {
        PlatformSetting s = settings.findById(KEY).orElseGet(() -> new PlatformSetting(KEY, "false"));
        s.setValue(Boolean.toString(enabled));
        settings.save(s);
        audit.record(actor, enabled ? "MAINTENANCE_ON" : "MAINTENANCE_OFF", KEY, null, ip);
        Runnable publish = () -> cached = enabled;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { publish.run(); }
            });
        } else {
            publish.run();
        }
        return enabled;
    }
}
