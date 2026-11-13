package com.dworf.slots_demo.unit;

import com.dworf.slots_demo.security.RateLimiter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class RateLimiterTest {

    private static final class FakeClock extends Clock {
        final AtomicLong now = new AtomicLong(1_000_000);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        @Override public long millis() { return now.get(); }
    }

    @Test
    void allowsUpToTheLimitThenBlocks() {
        RateLimiter rl = new RateLimiter(3, 1000, new FakeClock());
        assertTrue(rl.tryAcquire("k"));
        assertTrue(rl.tryAcquire("k"));
        assertTrue(rl.tryAcquire("k"));
        assertFalse(rl.tryAcquire("k"));
    }

    @Test
    void keysAreIndependent() {
        RateLimiter rl = new RateLimiter(1, 1000, new FakeClock());
        assertTrue(rl.tryAcquire("a"));
        assertFalse(rl.tryAcquire("a"));
        assertTrue(rl.tryAcquire("b"));
    }

    @Test
    void windowSlides() {
        FakeClock clock = new FakeClock();
        RateLimiter rl = new RateLimiter(2, 1000, clock);
        assertTrue(rl.tryAcquire("k"));
        assertTrue(rl.tryAcquire("k"));
        assertFalse(rl.tryAcquire("k"));
        clock.now.addAndGet(1001);
        assertTrue(rl.tryAcquire("k"));
    }

    @Test
    void blockedAttemptsDoNotExtendTheWindow() {
        FakeClock clock = new FakeClock();
        RateLimiter rl = new RateLimiter(1, 1000, clock);
        assertTrue(rl.tryAcquire("k"));
        clock.now.addAndGet(900);
        assertFalse(rl.tryAcquire("k"));
        clock.now.addAndGet(101);
        assertTrue(rl.tryAcquire("k"), "window must be measured from the last ACCEPTED attempt");
    }

    @Test
    void resetClearsTheKey() {
        RateLimiter rl = new RateLimiter(1, 1000, new FakeClock());
        assertTrue(rl.tryAcquire("k"));
        assertFalse(rl.tryAcquire("k"));
        rl.reset("k");
        assertTrue(rl.tryAcquire("k"));
    }

    @Test
    void isThreadSafe() throws Exception {
        RateLimiter rl = new RateLimiter(50, 60_000);
        java.util.concurrent.atomic.AtomicInteger ok = new java.util.concurrent.atomic.AtomicInteger();
        Thread[] ts = new Thread[16];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> { for (int j = 0; j < 20; j++) if (rl.tryAcquire("shared")) ok.incrementAndGet(); });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        org.junit.jupiter.api.Assertions.assertEquals(50, ok.get());
    }
}
