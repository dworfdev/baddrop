package com.dworf.slots_demo.service;

import com.dworf.slots_demo.security.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** H10: central place for all brute-force limits. */
@Component
public class AuthRateLimits {
    private static final long FIFTEEN_MIN = TimeUnit.MINUTES.toMillis(15);

    public final RateLimiter loginPerIp;
    public final RateLimiter adminLoginPerIp;

    /** Limits are properties so tests can raise them without touching production defaults. */
    public AuthRateLimits(
            @Value("${slots.limits.login-per-ip:30}") int loginPerIpMax,
            @Value("${slots.limits.admin-login-per-ip:5}") int adminLoginMax) {
        this.loginPerIp = new RateLimiter(loginPerIpMax, FIFTEEN_MIN);
        this.adminLoginPerIp = new RateLimiter(adminLoginMax, FIFTEEN_MIN);
    }

    @Scheduled(fixedDelay = 600_000)
    public void evict() {
        loginPerIp.evictIdle();
        adminLoginPerIp.evictIdle();
    }
}
