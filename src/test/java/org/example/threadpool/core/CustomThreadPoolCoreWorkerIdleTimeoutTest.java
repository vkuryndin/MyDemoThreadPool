package org.example.threadpool.core;

import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.metrics.PoolMetricsSnapshot;
import org.example.threadpool.rejection.RejectPolicy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for core worker behavior during idle timeout.
 *
 * These tests verify that a core worker must stay alive
 * even if it remains idle longer than keepAliveTime.
 */
class CustomThreadPoolCoreWorkerIdleTimeoutTest {

    /**
     * Verifies that the single core worker does not stop
     * after idle timeout when corePoolSize = maxPoolSize = 1.
     *
     * In this scenario:
     * - the pool starts with one core worker
     * - no tasks are submitted
     * - we wait longer than keepAliveTime
     * - the core worker must still exist
     */
    @Test
    void shouldKeepCoreWorkerAliveAfterIdleTimeout() throws Exception {
        CustomThreadPool pool = createSingleCoreWorkerPool("CoreIdleTimeoutPool");

        try {
            /**
             * Wait noticeably longer than keepAliveTime.
             *
             * keepAliveTime in this test pool is 300 ms,
             * so 1000 ms gives enough time for an incorrect implementation
             * to accidentally stop the worker.
             */
            Thread.sleep(1000);

            PoolMetricsSnapshot metrics = pool.getMetricsSnapshot();

            assertEquals(1, metrics.getCurrentWorkerCount());
            assertEquals(1, metrics.getPeakWorkerCount());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Creates a pool with exactly one core worker and no ability to grow.
     *
     * This configuration makes it easy to verify that the core worker
     * is never stopped by idle timeout.
     *
     * @param poolName logical pool name
     * @return configured CustomThreadPool
     */
    private CustomThreadPool createSingleCoreWorkerPool(String poolName) {
        PoolConfig config = new PoolConfig(
                1,
                1,
                300,
                TimeUnit.MILLISECONDS,
                1,
                0
        );

        return new CustomThreadPool(
                poolName,
                config,
                new RoundRobinBalancer(),
                new RejectPolicy()
        );
    }
}