package org.example.threadpool.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.rejection.RejectPolicy;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for submit() behavior of CustomThreadPool.
 *
 * <p>These tests verify: - submit() returns a Future - Future.get() returns the expected result -
 * multiple submitted tasks can be completed correctly
 */
class CustomThreadPoolSubmitTest {

  /** Verifies that submit() returns a Future with the expected result. */
  @Test
  void shouldReturnCorrectResultFromSubmit() throws Exception {
    CustomThreadPool pool = createTestPool("SubmitTestPool");

    try {
      Future<String> future = pool.submit(() -> "hello");

      assertEquals("hello", future.get());
    } finally {
      pool.shutdownNow();
    }
  }

  /** Verifies that multiple submitted tasks return correct results. */
  @Test
  void shouldReturnCorrectResultsForMultipleSubmittedTasks() throws Exception {
    CustomThreadPool pool = createTestPool("MultiSubmitTestPool");

    try {
      Future<Integer> future1 = pool.submit(() -> 10);
      Future<Integer> future2 = pool.submit(() -> 20);
      Future<Integer> future3 = pool.submit(() -> 30);

      assertEquals(10, future1.get());
      assertEquals(20, future2.get());
      assertEquals(30, future3.get());
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * Creates a small test pool with a stable configuration.
   *
   * @param poolName logical pool name
   * @return configured CustomThreadPool
   */
  private CustomThreadPool createTestPool(String poolName) {
    PoolConfig config = new PoolConfig(2, 4, 5, TimeUnit.SECONDS, 4, 1);

    return new CustomThreadPool(poolName, config, new RoundRobinBalancer(), new RejectPolicy());
  }
}
