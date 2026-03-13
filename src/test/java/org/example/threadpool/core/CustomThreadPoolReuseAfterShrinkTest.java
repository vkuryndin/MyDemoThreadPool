package org.example.threadpool.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.metrics.PoolMetricsSnapshot;
import org.example.threadpool.rejection.RejectPolicy;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for pool behavior after shrink-back to corePoolSize.
 *
 * <p>These tests verify that the pool remains usable after: - growing above corePoolSize -
 * shrinking back after idle timeout
 */
class CustomThreadPoolReuseAfterShrinkTest {

  /**
   * Verifies that after the pool grows and then shrinks back to corePoolSize, it can still accept
   * and execute new tasks correctly.
   */
  @Test
  void shouldAcceptAndExecuteNewTaskAfterShrinkBack() throws Exception {
    CustomThreadPool pool = createTestPool("ReuseAfterShrinkPool");

    CountDownLatch firstTaskStarted = new CountDownLatch(1);
    CountDownLatch releaseFirstTask = new CountDownLatch(1);
    CountDownLatch initialWorkCompleted = new CountDownLatch(3);
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
              initialWorkCompleted.countDown();
            }
          };

      Runnable secondTask =
          () -> {
            initialWorkCompleted.countDown();
          };

      Runnable thirdTask =
          () -> {
            thirdTaskExecuted.countDown();
            initialWorkCompleted.countDown();
          };

      /* Force the pool to grow from 1 worker to 2 workers. */
      pool.execute(firstTask);

      boolean firstStarted = firstTaskStarted.await(3, TimeUnit.SECONDS);
      assertTrue(firstStarted, "The first task did not start within the timeout");

      pool.execute(secondTask);
      pool.execute(thirdTask);

      boolean thirdRan = thirdTaskExecuted.await(3, TimeUnit.SECONDS);
      assertTrue(thirdRan, "The third task did not execute within the timeout");

      PoolMetricsSnapshot grownSnapshot = pool.getMetricsSnapshot();
      assertEquals(2, grownSnapshot.getPeakWorkerCount());

      /* Finish the initial workload. */
      releaseFirstTask.countDown();

      boolean initialCompleted = initialWorkCompleted.await(5, TimeUnit.SECONDS);
      assertTrue(initialCompleted, "The initial workload did not complete within the timeout");

      /* Wait until the extra worker disappears and the pool shrinks back. */
      boolean shrunk = waitUntilWorkerCountBecomes(pool, 1, 5, TimeUnit.SECONDS);
      assertTrue(shrunk, "The pool did not shrink back to corePoolSize within the timeout");

      /* Now submit a brand new task after shrink-back. The pool must still be usable. */
      CountDownLatch newTaskCompleted = new CountDownLatch(1);
      AtomicInteger counter = new AtomicInteger(0);

      pool.execute(
          () -> {
            counter.incrementAndGet();
            newTaskCompleted.countDown();
          });

      boolean completed = newTaskCompleted.await(3, TimeUnit.SECONDS);
      assertTrue(completed, "The new task after shrink-back did not complete within the timeout");
      assertEquals(1, counter.get());

      PoolMetricsSnapshot finalSnapshot = pool.getMetricsSnapshot();

      assertEquals(4, finalSnapshot.getSubmittedTaskCount());
      assertEquals(4, finalSnapshot.getAcceptedTaskCount());
      assertEquals(0, finalSnapshot.getRejectedTaskCount());
      assertEquals(4, finalSnapshot.getCompletedTaskCount());
    } finally {
      releaseFirstTask.countDown();
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
   * Creates a deterministic pool configuration for grow-shrink-reuse testing.
   *
   * @param poolName logical pool name
   * @return configured CustomThreadPool
   */
  private CustomThreadPool createTestPool(String poolName) {
    PoolConfig config = new PoolConfig(1, 2, 300, TimeUnit.MILLISECONDS, 1, 0);

    return new CustomThreadPool(poolName, config, new RoundRobinBalancer(), new RejectPolicy());
  }
}
