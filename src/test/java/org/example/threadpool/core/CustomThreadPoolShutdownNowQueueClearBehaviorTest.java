package org.example.threadpool.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.metrics.PoolMetricsSnapshot;
import org.example.threadpool.rejection.RejectPolicy;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for shutdownNow() queue-clearing behavior.
 *
 * <p>These tests verify that a task accepted into a worker queue is not executed if shutdownNow()
 * clears the queue before the task starts.
 */
class CustomThreadPoolShutdownNowQueueClearBehaviorTest {

  /**
   * Verifies that a queued task does not execute after shutdownNow().
   *
   * <p>Test scenario: - task 1 starts running and blocks - task 2 is accepted into the queue -
   * shutdownNow() interrupts task 1 and clears task 2 from the queue - task 2 must never run
   */
  @Test
  void shouldNotExecuteQueuedTaskAfterShutdownNowClearsQueue() throws Exception {
    CustomThreadPool pool = createSingleWorkerTestPool("ShutdownNowQueueBehaviorPool");

    CountDownLatch firstTaskStarted = new CountDownLatch(1);
    CountDownLatch firstTaskFinished = new CountDownLatch(1);
    CountDownLatch blockFirstTask = new CountDownLatch(1);

    AtomicInteger executedTaskCounter = new AtomicInteger(0);
    AtomicBoolean secondTaskExecuted = new AtomicBoolean(false);

    try {
      Runnable firstTask =
          () -> {
            firstTaskStarted.countDown();

            try {
              blockFirstTask.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              executedTaskCounter.incrementAndGet();
              firstTaskFinished.countDown();
            }
          };

      Runnable secondTask =
          () -> {
            secondTaskExecuted.set(true);
            executedTaskCounter.incrementAndGet();
          };

      /* Start the first task and wait until it is definitely running. */
      pool.execute(firstTask);

      boolean started = firstTaskStarted.await(3, TimeUnit.SECONDS);
      assertTrue(started, "The first task did not start within the timeout");

      /* Submit the second task. With one worker, it should stay in the queue. */
      pool.execute(secondTask);

      /*
       * Immediate shutdown should interrupt the running task and clear the queued task before it
       * starts.
       */
      pool.shutdownNow();

      boolean finished = firstTaskFinished.await(3, TimeUnit.SECONDS);
      assertTrue(finished, "The interrupted first task did not finish within the timeout");

      /*
       * Give the pool a short extra moment to expose a possible bug where the queued second task
       * could still run incorrectly.
       */
      Thread.sleep(300);

      /* Only the first task should have executed. The queued second task must never run. */
      assertEquals(1, executedTaskCounter.get());
      assertFalse(
          secondTaskExecuted.get(), "The queued task should not execute after shutdownNow()");

      /* Optional additional validation through metrics. */
      PoolMetricsSnapshot metrics = pool.getMetricsSnapshot();

      assertEquals(2, metrics.getSubmittedTaskCount());
      assertEquals(2, metrics.getAcceptedTaskCount());
      assertEquals(0, metrics.getRejectedTaskCount());
      assertEquals(1, metrics.getCompletedTaskCount());
      assertEquals(0, metrics.getCurrentPendingTaskCount());
    } finally {
      /*
       * Release the latch just in case the first task was not interrupted because of an earlier
       * unexpected failure in the test.
       */
      blockFirstTask.countDown();
      pool.shutdownNow();
    }
  }

  /**
   * Creates a deterministic single-worker pool for shutdownNow() queue tests.
   *
   * @param poolName logical pool name
   * @return configured CustomThreadPool
   */
  private CustomThreadPool createSingleWorkerTestPool(String poolName) {
    PoolConfig config = new PoolConfig(1, 1, 5, TimeUnit.SECONDS, 2, 0);

    return new CustomThreadPool(poolName, config, new RoundRobinBalancer(), new RejectPolicy());
  }
}
