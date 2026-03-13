package org.example.threadpool.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.metrics.PoolMetricsSnapshot;
import org.example.threadpool.rejection.RejectPolicy;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for metrics behavior during shutdownNow().
 *
 * <p>These tests verify that accepted tasks may be greater than completed tasks when pending tasks
 * are cleared from queues during immediate shutdown.
 */
class CustomThreadPoolShutdownNowMetricsTest {

  /**
   * Verifies that shutdownNow() may leave a gap between accepted and completed tasks.
   *
   * <p>Test scenario: - one task starts running and waits on a latch - the second task is accepted
   * into the queue - shutdownNow() interrupts the running task and clears the queued task
   *
   * <p>Expected result: - both tasks were accepted - only the running task completed - the queued
   * task was removed before execution
   */
  @Test
  void shouldHaveMoreAcceptedThanCompletedTasksAfterShutdownNow() throws Exception {
    CustomThreadPool pool = createSingleWorkerTestPool("ShutdownNowMetricsPool");

    CountDownLatch firstTaskStarted = new CountDownLatch(1);
    CountDownLatch firstTaskFinished = new CountDownLatch(1);
    CountDownLatch blockFirstTask = new CountDownLatch(1);

    try {
      Runnable firstTask =
          () -> {
            firstTaskStarted.countDown();

            try {
              /**
               * The task waits until it is either released normally or interrupted by
               * shutdownNow().
               */
              blockFirstTask.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              firstTaskFinished.countDown();
            }
          };

      Runnable secondTask =
          () -> {
            /**
             * This task should never run in this scenario, because it is expected to be cleared
             * from the queue.
             */
          };

      /** Submit the first task and wait until it really starts running. */
      pool.execute(firstTask);

      boolean started = firstTaskStarted.await(3, TimeUnit.SECONDS);
      assertTrue(started, "The first task did not start within the timeout");

      /**
       * Submit the second task. Because the pool has only one worker, this task should be accepted
       * into the queue and remain pending there.
       */
      pool.execute(secondTask);

      /**
       * Immediate shutdown should: - interrupt the running first task - clear the queued second
       * task
       */
      pool.shutdownNow();

      boolean finished = firstTaskFinished.await(3, TimeUnit.SECONDS);
      assertTrue(finished, "The interrupted running task did not finish within the timeout");

      PoolMetricsSnapshot metrics = pool.getMetricsSnapshot();

      assertEquals(2, metrics.getSubmittedTaskCount());
      assertEquals(2, metrics.getAcceptedTaskCount());
      assertEquals(0, metrics.getRejectedTaskCount());
      assertEquals(1, metrics.getCompletedTaskCount());

      /**
       * This is the key property of this test: one accepted task was cleared from the queue and
       * never executed.
       */
      assertTrue(metrics.getAcceptedTaskCount() > metrics.getCompletedTaskCount());

      /** After shutdownNow(), pending queues should be cleared. */
      assertEquals(0, metrics.getCurrentPendingTaskCount());
    } finally {
      /**
       * Release the latch just in case the task was not interrupted due to an unexpected failure
       * earlier in the test.
       */
      blockFirstTask.countDown();
      pool.shutdownNow();
    }
  }

  /**
   * Creates a deterministic single-worker pool for shutdownNow() metrics testing.
   *
   * @param poolName logical pool name
   * @return configured CustomThreadPool
   */
  private CustomThreadPool createSingleWorkerTestPool(String poolName) {
    PoolConfig config = new PoolConfig(1, 1, 5, TimeUnit.SECONDS, 2, 0);

    return new CustomThreadPool(poolName, config, new RoundRobinBalancer(), new RejectPolicy());
  }
}
