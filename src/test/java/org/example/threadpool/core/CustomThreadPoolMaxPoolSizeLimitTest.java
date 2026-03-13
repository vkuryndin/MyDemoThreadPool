package org.example.threadpool.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.metrics.PoolMetricsSnapshot;
import org.example.threadpool.rejection.RejectPolicy;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for maxPoolSize limit behavior of CustomThreadPool.
 *
 * <p>These tests verify that the pool does not grow beyond maxPoolSize and rejects extra tasks when
 * all workers and queues are already full.
 */
class CustomThreadPoolMaxPoolSizeLimitTest {

  /**
   * Verifies that the pool rejects tasks after reaching maxPoolSize and filling all available
   * worker queues.
   *
   * <p>Test scenario: - worker 1 runs task 1 - worker 1 queue stores task 2 - pool grows and worker
   * 2 runs task 3 - worker 2 queue stores task 4 - task 5 must be rejected because maxPoolSize is
   * already reached
   */
  @Test
  void shouldRejectTaskWhenMaxPoolSizeAndQueuesAreFullyUsed() throws Exception {
    CustomThreadPool pool = createLimitedGrowthPool("MaxPoolLimitTestPool");

    CountDownLatch firstTaskStarted = new CountDownLatch(1);
    CountDownLatch thirdTaskStarted = new CountDownLatch(1);
    CountDownLatch releaseBlockingTasks = new CountDownLatch(1);

    try {
      Runnable firstTask =
          () -> {
            firstTaskStarted.countDown();

            try {
              releaseBlockingTasks.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          };

      Runnable secondTask =
          () -> {
            // This task is used to fill the queue of worker 1.
          };

      Runnable thirdTask =
          () -> {
            thirdTaskStarted.countDown();

            try {
              releaseBlockingTasks.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          };

      Runnable fourthTask =
          () -> {
            // This task is used to fill the queue of worker 2.
          };

      /** Task 1 starts running on worker 1. */
      pool.execute(firstTask);

      boolean firstStarted = firstTaskStarted.await(3, TimeUnit.SECONDS);
      assertTrue(firstStarted, "The first task did not start within the timeout");

      /** Task 2 fills the queue of worker 1. */
      pool.execute(secondTask);

      /** Task 3 should trigger pool growth and start on worker 2. */
      pool.execute(thirdTask);

      boolean thirdStarted = thirdTaskStarted.await(3, TimeUnit.SECONDS);
      assertTrue(thirdStarted, "The third task did not start within the timeout");

      /** Task 4 fills the queue of worker 2. */
      pool.execute(fourthTask);

      /**
       * Now all capacity is exhausted: - 2 workers exist (maxPoolSize reached) - both worker queues
       * are full
       *
       * <p>Task 5 must be rejected.
       */
      assertThrows(
          RejectedExecutionException.class,
          () ->
              pool.execute(
                  () -> {
                    // This task must be rejected.
                  }));

      PoolMetricsSnapshot metrics = pool.getMetricsSnapshot();

      assertEquals(5, metrics.getSubmittedTaskCount());
      assertEquals(4, metrics.getAcceptedTaskCount());
      assertEquals(1, metrics.getRejectedTaskCount());
      assertEquals(2, metrics.getPeakWorkerCount());
    } finally {
      releaseBlockingTasks.countDown();
      pool.shutdownNow();
    }
  }

  /**
   * Creates a pool with a very small capacity, so that maxPoolSize behavior is easy to observe.
   *
   * @param poolName logical pool name
   * @return configured CustomThreadPool
   */
  private CustomThreadPool createLimitedGrowthPool(String poolName) {
    PoolConfig config = new PoolConfig(1, 2, 5, TimeUnit.SECONDS, 1, 0);

    return new CustomThreadPool(poolName, config, new RoundRobinBalancer(), new RejectPolicy());
  }
}
