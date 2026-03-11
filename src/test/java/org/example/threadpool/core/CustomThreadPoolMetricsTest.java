package org.example.threadpool.core;

import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.metrics.PoolMetricsSnapshot;
import org.example.threadpool.rejection.RejectPolicy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for runtime metrics of CustomThreadPool.
 *
 * These tests verify:
 * - submitted / accepted / completed counters for normal execution
 * - submitted / rejected counters for rejected tasks
 */
class CustomThreadPoolMetricsTest {

    /**
     * Verifies that the pool correctly tracks submitted, accepted,
     * rejected, and completed tasks during normal execution.
     */
    @Test
    void shouldTrackMetricsForSuccessfullyExecutedTasks() throws Exception {
        CustomThreadPool pool = createTestPool("MetricsSuccessPool");

        try {
            CountDownLatch latch = new CountDownLatch(3);

            Runnable task = () -> {
                latch.countDown();
            };

            pool.execute(task);
            pool.execute(task);
            pool.execute(task);

            boolean completed = latch.await(3, TimeUnit.SECONDS);
            assertTrue(completed, "Tasks were not completed within the timeout");

            PoolMetricsSnapshot metrics = pool.getMetricsSnapshot();

            assertEquals(3, metrics.getSubmittedTaskCount());
            assertEquals(3, metrics.getAcceptedTaskCount());
            assertEquals(0, metrics.getRejectedTaskCount());
            assertEquals(3, metrics.getCompletedTaskCount());

            /**
             * At least one worker must have existed during execution.
             */
            assertTrue(metrics.getPeakWorkerCount() >= 1);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Verifies that the pool correctly tracks rejected tasks
     * after shutdown has already started.
     */
    @Test
    void shouldTrackMetricsForRejectedTasksAfterShutdown() {
        CustomThreadPool pool = createTestPool("MetricsRejectedPool");

        try {
            pool.shutdown();

            try {
                pool.execute(() -> {
                    // This task must be rejected after shutdown.
                });
            } catch (RejectedExecutionException ignored) {
                // Rejection is expected in this test.
            }

            try {
                pool.submit(() -> "result");
            } catch (RejectedExecutionException ignored) {
                // Rejection is expected in this test.
            }

            PoolMetricsSnapshot metrics = pool.getMetricsSnapshot();

            assertEquals(2, metrics.getSubmittedTaskCount());
            assertEquals(0, metrics.getAcceptedTaskCount());
            assertEquals(2, metrics.getRejectedTaskCount());
            assertEquals(0, metrics.getCompletedTaskCount());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Creates a stable test pool configuration for metrics tests.
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