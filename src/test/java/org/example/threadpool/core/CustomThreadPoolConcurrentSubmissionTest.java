package org.example.threadpool.core;

import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.metrics.PoolMetricsSnapshot;
import org.example.threadpool.rejection.RejectPolicy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controlled concurrent-submission tests for CustomThreadPool.
 *
 * These tests verify that under concurrent task submission:
 * - submitted = accepted + rejected
 * - after graceful shutdown, completed = accepted
 * - task execution count matches completedTaskCount
 */
class CustomThreadPoolConcurrentSubmissionTest {

    /**
     * Verifies that concurrent submissions keep pool metrics consistent.
     *
     * Test scenario:
     * - several external threads submit tasks at the same time
     * - some tasks may be accepted, some may be rejected
     * - after submission finishes, graceful shutdown is started
     * - all accepted tasks must complete
     */
    @Test
    void shouldKeepMetricsConsistentUnderConcurrentSubmission() throws Exception {
        CustomThreadPool pool = createConcurrentTestPool("ConcurrentSubmissionPool");

        int submitterThreadCount = 4;
        int tasksPerSubmitter = 15;
        int totalTasks = submitterThreadCount * tasksPerSubmitter;

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch submittersFinished = new CountDownLatch(submitterThreadCount);

        AtomicInteger externallyObservedRejections = new AtomicInteger(0);
        AtomicInteger actuallyExecutedTasks = new AtomicInteger(0);

        try {
            for (int i = 0; i < submitterThreadCount; i++) {
                Thread submitter = new Thread(() -> {
                    try {
                        startGate.await();

                        for (int j = 0; j < tasksPerSubmitter; j++) {
                            try {
                                pool.execute(() -> {
                                    actuallyExecutedTasks.incrementAndGet();

                                    try {
                                        /**
                                         * Small sleep creates a little overlap between tasks
                                         * and increases the chance of queue pressure.
                                         */
                                        Thread.sleep(20);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                });
                            } catch (RejectedExecutionException e) {
                                externallyObservedRejections.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        submittersFinished.countDown();
                    }
                });

                submitter.start();
            }

            /**
             * Start all submitter threads at roughly the same time.
             */
            startGate.countDown();

            boolean allSubmittersFinished = submittersFinished.await(5, TimeUnit.SECONDS);
            assertTrue(allSubmittersFinished, "Submitter threads did not finish within the timeout");

            /**
             * After all submissions are done, start graceful shutdown.
             * All accepted tasks must still complete.
             */
            pool.shutdown();

            boolean fullyStopped = waitUntilWorkerCountBecomes(pool, 0, 10, TimeUnit.SECONDS);
            assertTrue(fullyStopped, "The pool did not finish graceful shutdown within the timeout");

            PoolMetricsSnapshot metrics = pool.getMetricsSnapshot();

            assertEquals(totalTasks, metrics.getSubmittedTaskCount());

            /**
             * Core consistency rule:
             * every submitted task must be either accepted or rejected.
             */
            assertEquals(
                    metrics.getSubmittedTaskCount(),
                    metrics.getAcceptedTaskCount() + metrics.getRejectedTaskCount()
            );

            /**
             * After graceful shutdown completes, all accepted tasks must be completed.
             */
            assertEquals(metrics.getAcceptedTaskCount(), metrics.getCompletedTaskCount());

            /**
             * External observation of rejections should match pool metrics.
             */
            assertEquals(externallyObservedRejections.get(), metrics.getRejectedTaskCount());

            /**
             * Executed task count should match completed task count.
             */
            assertEquals(actuallyExecutedTasks.get(), metrics.getCompletedTaskCount());

            /**
             * After full graceful shutdown, all workers should be gone.
             */
            assertEquals(0, metrics.getCurrentWorkerCount());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Waits until the pool reports the expected current worker count.
     *
     * @param pool the pool to observe
     * @param expectedWorkerCount expected current worker count
     * @param timeout how long to wait
     * @param unit timeout unit
     * @return true if the expected worker count was observed in time
     * @throws InterruptedException if the waiting thread is interrupted
     */
    private boolean waitUntilWorkerCountBecomes(CustomThreadPool pool,
                                                int expectedWorkerCount,
                                                long timeout,
                                                TimeUnit unit) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);

        while (System.nanoTime() < deadlineNanos) {
            if (pool.getMetricsSnapshot().getCurrentWorkerCount() == expectedWorkerCount) {
                return true;
            }

            Thread.sleep(50);
        }

        return false;
    }

    /**
     * Creates a stable pool for concurrent-submission testing.
     *
     * The configuration is intentionally moderate:
     * - enough capacity for real concurrent execution
     * - still small enough to allow some rejection under pressure
     *
     * @param poolName logical pool name
     * @return configured CustomThreadPool
     */
    private CustomThreadPool createConcurrentTestPool(String poolName) {
        PoolConfig config = new PoolConfig(
                2,
                4,
                500,
                TimeUnit.MILLISECONDS,
                2,
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