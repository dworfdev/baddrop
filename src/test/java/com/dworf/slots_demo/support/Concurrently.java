package com.dworf.slots_demo.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Starts N tasks behind a latch so they hit the server as simultaneously as the JVM allows. */
public final class Concurrently {
    private Concurrently() {}

    public static <T> List<T> run(int n, Callable<T> task) {
        return runEach(n, i -> task.call());
    }

    public interface IndexedTask<T> {
        T call(int index) throws Exception;
    }

    public static <T> List<T> runEach(int n, IndexedTask<T> task) {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch ready = new CountDownLatch(n);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call(idx);
                }));
            }
            if (!ready.await(30, TimeUnit.SECONDS)) throw new AssertionError("workers did not start");
            go.countDown();
            List<T> out = new ArrayList<>();
            for (Future<T> f : futures) out.add(f.get(180, TimeUnit.SECONDS));
            return out;
        } catch (Exception e) {
            throw new AssertionError("concurrent run failed: " + e, e);
        } finally {
            pool.shutdownNow();
        }
    }

    public static long count(List<ApiClient.Response> rs, int status) {
        return rs.stream().filter(r -> r.status() == status).count();
    }
}
