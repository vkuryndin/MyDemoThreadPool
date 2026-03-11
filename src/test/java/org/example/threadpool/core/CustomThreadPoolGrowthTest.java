package org.example.threadpool.core;

import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.metrics.PoolMetricsSnapshot;
import org.example.threadpool.rejection.RejectPolicy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for worker growth behavior of CustomThreadPool.
 *
 * These tests verify that the pool can grow above corePoolSize
 * when the current worker capacity is exhausted.
 */
class CustomThreadPoolGrowthTest {

    /**
     * Verifies that the pool creates an additional worker
     * when the first worker is busy and its queue is already full.
     *
     * Test scenario:
     * - one worker is created initially
     * - the first task starts running and blocks
     * - the second task fills the first worker queue
     * - the third task triggers pool growth
     * - the third task should run on the newly created worker
     */
    @Test
    void shouldGrowBeyondCorePoolSizeWhenLoadExceedsCapacity() throws Exception {
        CustomThreadPool pool = createGrowthTestPool("GrowthTestPool");

        CountDownLatch firstTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstTask = new CountDownLatch(1);
        CountDownLatch thirdTaskExecuted = new CountDownLatch(1);

        try {
            Runnable firstTask = () -> {
                firstTaskStarted.countDown();

                try {
                    releaseFirstTask.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };

            Runnable secondTask = () -> {
                // This task only occupies the queue of the first worker.
            };

            Runnable thirdTask = () -> {
                thirdTaskExecuted.countDown();
            };

            /**
             * Submit the first task and wait until it really starts running.
             */
            pool.execute(firstTask);

            boolean started = firstTaskStarted.await(3, TimeUnit.SECONDS);
            assertTrue(started, "The first task did not start within the timeout");

            /**
             * Submit the second task.
             * With one worker and queueSize = 1, this should fill the queue.
             */
            pool.execute(secondTask);

            /**
             * Submit the third task.
             * Because the first worker is busy and its queue is full,
             * the pool should create a second worker.
             */
            pool.execute(thirdTask);

            boolean thirdTaskRan = thirdTaskExecuted.await(3, TimeUnit.SECONDS);
            assertTrue(thirdTaskRan, "The third task did not execute within the timeout");

            PoolMetricsSnapshot metrics = pool.getMetricsSnapshot();

            assertEquals(3, metrics.getSubmittedTaskCount());
            assertEquals(3, metrics.getAcceptedTaskCount());
            assertEquals(0, metrics.getRejectedTaskCount());

            /**
             * The key assertion of this test:
             * the pool must have grown from 1 worker to 2 workers.
             */
            assertEquals(2, metrics.getPeakWorkerCount());
        } finally {
            releaseFirstTask.countDown();
            pool.shutdownNow();
        }
    }

    /**
     * Creates a small deterministic pool configuration
     * that makes worker growth easy to trigger and observe.
     *
     * @param poolName logical pool name
     * @return configured CustomThreadPool
     */
    private CustomThreadPool createGrowthTestPool(String poolName) {
        PoolConfig config = new PoolConfig(
                1,
                2,
                5,
                TimeUnit.SECONDS,
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