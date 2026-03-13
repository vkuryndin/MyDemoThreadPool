package org.example.threadpool.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.rejection.RejectPolicy;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for graceful shutdown behavior of CustomThreadPool.
 *
 * <p>These tests verify that: - already accepted tasks are allowed to finish after shutdown() - new
 * tasks are rejected after shutdown()
 */
class CustomThreadPoolGracefulShutdownTest {

  /**
   * Verifies that shutdown() does not cancel already accepted tasks.
   *
   * <p>The test uses a pool with one worker: - the first task starts running and waits on a latch -
   * the second task is accepted and stays in the queue - shutdown() is called - a new task is
   * rejected - the first task is released - both accepted tasks must complete successfully
   */
  @Test
  void shouldCompleteAlreadyAcceptedTasksAfterShutdown() throws Exception {
    CustomThreadPool pool = createSingleWorkerTestPool("GracefulShutdownPool");

    CountDownLatch firstTaskStarted = new CountDownLatch(1);
    CountDownLatch allowFirstTaskToFinish = new CountDownLatch(1);
    CountDownLatch allAcceptedTasksCompleted = new CountDownLatch(2);
    AtomicInteger completedTaskCounter = new AtomicInteger(0);

    try {
      Runnable firstTask =
          () -> {
            firstTaskStarted.countDown();

            try {
              allowFirstTaskToFinish.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            }

            completedTaskCounter.incrementAndGet();
            allAcceptedTasksCompleted.countDown();
          };

      Runnable secondTask =
          () -> {
            completedTaskCounter.incrementAndGet();
            allAcceptedTasksCompleted.countDown();
          };

      /* Submit the first task and wait until it really starts running. */
      pool.execute(firstTask);

      boolean firstTaskReallyStarted = firstTaskStarted.await(3, TimeUnit.SECONDS);
      assertTrue(firstTaskReallyStarted, "The first task did not start within the timeout");

      /*
       * Submit the second task. Because the pool has only one worker, this task should be accepted
       * into the queue and wait there.
       */
      pool.execute(secondTask);

      /* Start graceful shutdown. Already accepted tasks must still be allowed to finish. */
      pool.shutdown();

      /* Any new task after shutdown() must be rejected. */
      assertThrows(
          RejectedExecutionException.class,
          () ->
              pool.execute(
                  () -> {
                    // This task must not be accepted after shutdown().
                  }));

      /*
       * Allow the first running task to finish. After that, the queued second task should also
       * execute.
       */
      allowFirstTaskToFinish.countDown();

      boolean allCompleted = allAcceptedTasksCompleted.await(5, TimeUnit.SECONDS);

      assertTrue(allCompleted, "Accepted tasks did not complete within the timeout");
      assertEquals(2, completedTaskCounter.get());
    } finally {
      /* Ensure the pool is fully stopped even if the test fails. */
      allowFirstTaskToFinish.countDown();
      pool.shutdownNow();
    }
  }

  /**
   * Creates a deterministic test pool with exactly one worker.
   *
   * <p>This makes it easier to test queued behavior during graceful shutdown.
   *
   * @param poolName logical pool name
   * @return configured CustomThreadPool
   */
  private CustomThreadPool createSingleWorkerTestPool(String poolName) {
    PoolConfig config = new PoolConfig(1, 1, 5, TimeUnit.SECONDS, 2, 0);

    return new CustomThreadPool(poolName, config, new RoundRobinBalancer(), new RejectPolicy());
  }
}
