package com.dworf.slots_demo.security;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** H10: in-memory sliding-window limiter. Single-node only; swap for Redis when scaling out. */
public final class RateLimiter {
    private final int maxAttempts;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter(int maxAttempts, long windowMillis, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    public RateLimiter(int maxAttempts, long windowMillis) {
        this(maxAttempts, windowMillis, Clock.systemUTC());
    }

    /** Records an attempt; returns false when the key is over the limit. */
    public boolean tryAcquire(String key) {
        long now = clock.millis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() >= windowMillis) q.pollFirst();
            if (q.size() >= maxAttempts) return false;
            q.addLast(now);
            return true;
        }
    }

    public void reset(String key) {
        hits.remove(key);
    }

    /** Drop idle keys; call from a scheduled task to bound memory. */
    public void evictIdle() {
        long now = clock.millis();
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                while (!e.getValue().isEmpty() && now - e.getValue().peekFirst() >= windowMillis) e.getValue().pollFirst();
                return e.getValue().isEmpty();
            }
        });
    }
}
