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
    public final RateLimiter loginPerUserAndIp;
    public final RateLimiter registerPerIp;
    public final RateLimiter adminLoginPerIp;
    public final RateLimiter passwordCheckPerUser;

    /** Limits are properties so tests can raise them without touching production defaults. */
    public AuthRateLimits(
            @Value("${slots.limits.login-per-ip:30}") int loginPerIpMax,
            @Value("${slots.limits.login-per-user-ip:5}") int loginPerUserIpMax,
            @Value("${slots.limits.register-per-ip:10}") int registerPerIpMax,
            @Value("${slots.limits.admin-login-per-ip:5}") int adminLoginMax,
            @Value("${slots.limits.password-check-per-user:5}") int passwordCheckMax) {
        this.loginPerIp = new RateLimiter(loginPerIpMax, FIFTEEN_MIN);
        this.loginPerUserAndIp = new RateLimiter(loginPerUserIpMax, FIFTEEN_MIN);
        this.registerPerIp = new RateLimiter(registerPerIpMax, TimeUnit.HOURS.toMillis(1));
        this.adminLoginPerIp = new RateLimiter(adminLoginMax, FIFTEEN_MIN);
        this.passwordCheckPerUser = new RateLimiter(passwordCheckMax, FIFTEEN_MIN);
    }

    @Scheduled(fixedDelay = 600_000)
    public void evict() {
        loginPerIp.evictIdle();
        loginPerUserAndIp.evictIdle();
        registerPerIp.evictIdle();
        adminLoginPerIp.evictIdle();
        passwordCheckPerUser.evictIdle();
    }
}
