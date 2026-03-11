package org.example.threadpool.core;

import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.rejection.RejectPolicy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for execute() behavior of CustomThreadPool.
 *
 * These tests verify:
 * - execute() really runs submitted Runnable tasks
 * - multiple Runnable tasks can be completed correctly
 */
class CustomThreadPoolExecuteTest {

    /**
     * Verifies that a single Runnable task submitted via execute()
     * is actually executed by the pool.
     */
    @Test
    void shouldExecuteSingleRunnableTask() throws Exception {
        CustomThreadPool pool = createTestPool("ExecuteTestPool");

        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger counter = new AtomicInteger(0);

            pool.execute(() -> {
                counter.incrementAndGet();
                latch.countDown();
            });

            boolean completed = latch.await(3, TimeUnit.SECONDS);

            assertTrue(completed, "The task was not completed within the timeout");
            assertEquals(1, counter.get());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Verifies that multiple Runnable tasks submitted via execute()
     * are all executed by the pool.
     */
    @Test
    void shouldExecuteMultipleRunnableTasks() throws Exception {
        CustomThreadPool pool = createTestPool("MultiExecuteTestPool");

        try {
            CountDownLatch latch = new CountDownLatch(3);
            AtomicInteger counter = new AtomicInteger(0);

            Runnable task = () -> {
                counter.incrementAndGet();
                latch.countDown();
            };

            pool.execute(task);
            pool.execute(task);
            pool.execute(task);

            boolean completed = latch.await(3, TimeUnit.SECONDS);

            assertTrue(completed, "Not all tasks were completed within the timeout");
            assertEquals(3, counter.get());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Creates a small test pool with a stable configuration.
     *
     * @param poolName logical pool name
     * @return configured CustomThreadPool
     */
    private CustomThreadPool createTestPool(String poolName) {
        PoolConfig config = new PoolConfig(
                2,
                4,
                5,
                TimeUnit.SECONDS,
                4,
                1
        );

        return new CustomThreadPool(
                poolName,
                config,
                new RoundRobinBalancer(),
                new RejectPolicy()
        );
    }
}