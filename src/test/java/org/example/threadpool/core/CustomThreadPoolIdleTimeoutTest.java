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
 * Unit tests for idle timeout behavior of CustomThreadPool.
 *
 * <p>These tests verify that an extra worker created during load can terminate after being idle
 * longer than keepAliveTime, while the pool still keeps corePoolSize workers alive.
 */
class CustomThreadPoolIdleTimeoutTest {

  /**
   * Verifies that the pool shrinks back to corePoolSize after an extra worker becomes idle.
   *
   * <p>Test scenario: - one worker exists initially - first task blocks worker 1 - second task
   * fills worker 1 queue - third task triggers growth to worker 2 - after tasks finish, worker 2
   * becomes idle - worker 2 should terminate after keepAliveTime
   */
  @Test
  void shouldShrinkBackToCorePoolSizeAfterIdleTimeout() throws Exception {
    CustomThreadPool pool = createIdleTimeoutTestPool("IdleTimeoutTestPool");

    CountDownLatch firstTaskStarted = new CountDownLatch(1);
    CountDownLatch releaseFirstTask = new CountDownLatch(1);
    CountDownLatch allTasksCompleted = new CountDownLatch(3);
    CountDownLatch thirdTaskExecuted = new CountDownLatch(1);

    try {
      Runnable firstTask =
          () -> {
            firstTaskStarted.countDown();

            try {
              releaseFirstTask.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              allTasksCompleted.countDown();
            }
          };

      Runnable secondTask =
          () -> {
            allTasksCompleted.countDown();
          };

      Runnable thirdTask =
          () -> {
            thirdTaskExecuted.countDown();
            allTasksCompleted.countDown();
          };

      /** Task 1 starts running on the core worker. */
      pool.execute(firstTask);

      boolean firstStarted = firstTaskStarted.await(3, TimeUnit.SECONDS);
      assertTrue(firstStarted, "The first task did not start within the timeout");

      /** Task 2 fills the queue of worker 1. */
      pool.execute(secondTask);

      /** Task 3 should trigger creation of an extra worker. */
      pool.execute(thirdTask);

      boolean thirdRan = thirdTaskExecuted.await(3, TimeUnit.SECONDS);
      assertTrue(thirdRan, "The third task did not execute within the timeout");

      /** At this point the pool must have grown to 2 workers. */
      PoolMetricsSnapshot grownSnapshot = pool.getMetricsSnapshot();
      assertEquals(2, grownSnapshot.getPeakWorkerCount());

      /** Allow the blocked first task to finish, so that the whole workload completes. */
      releaseFirstTask.countDown();

      boolean allCompleted = allTasksCompleted.await(5, TimeUnit.SECONDS);
      assertTrue(allCompleted, "Not all tasks completed within the timeout");

      /** Wait until the extra worker times out and the pool shrinks back. */
      boolean shrunk = waitUntilWorkerCountBecomes(pool, 1, 5, TimeUnit.SECONDS);
      assertTrue(shrunk, "The pool did not shrink back to corePoolSize within the timeout");

      PoolMetricsSnapshot finalSnapshot = pool.getMetricsSnapshot();

      assertEquals(1, finalSnapshot.getCurrentWorkerCount());
      assertEquals(3, finalSnapshot.getSubmittedTaskCount());
      assertEquals(3, finalSnapshot.getAcceptedTaskCount());
      assertEquals(0, finalSnapshot.getRejectedTaskCount());
      assertEquals(3, finalSnapshot.getCompletedTaskCount());
    } finally {
      releaseFirstTask.countDown();
      pool.shutdownNow();
    }
  }

  /**
   * Waits until the pool reports the expected current worker count.
   *
   * <p>This helper avoids relying on one fixed sleep duration, which would make the test more
   * fragile.
   *
   * @param pool the pool to observe
   * @param expectedWorkerCount expected current worker count
   * @param timeout how long to wait
   * @param unit timeout unit
   * @return true if the expected worker count was observed in time
   * @throws InterruptedException if the waiting thread is interrupted
   */
  private boolean waitUntilWorkerCountBecomes(
      CustomThreadPool pool, int expectedWorkerCount, long timeout, TimeUnit unit)
      throws InterruptedException {
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
   * Creates a deterministic pool configuration for idle-timeout testing.
   *
   * <p>The keepAliveTime is intentionally short so that the test finishes quickly.
   *
   * @param poolName logical pool name
   * @return configured CustomThreadPool
   */
  private CustomThreadPool createIdleTimeoutTestPool(String poolName) {
    PoolConfig config = new PoolConfig(1, 2, 300, TimeUnit.MILLISECONDS, 1, 0);

    return new CustomThreadPool(poolName, config, new RoundRobinBalancer(), new RejectPolicy());
  }
}
