package org.example.threadpool.core;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.rejection.RejectPolicy;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for shutdown behavior of CustomThreadPool.
 *
 * <p>These tests verify: - execute() rejects new tasks after shutdown() - execute() rejects new
 * tasks after shutdownNow() - submit() rejects new tasks after shutdown() - submit() rejects new
 * tasks after shutdownNow()
 */
class CustomThreadPoolShutdownTest {

  /** Verifies that execute() does not accept new tasks after graceful shutdown. */
  @Test
  void shouldRejectExecuteAfterShutdown() {
    CustomThreadPool pool = createTestPool("ShutdownTestPool");

    pool.shutdown();

    assertThrows(
        RejectedExecutionException.class,
        () ->
            pool.execute(
                () -> {
                  // This task must not be accepted after shutdown.
                }));
  }

  /** Verifies that execute() does not accept new tasks after immediate shutdown. */
  @Test
  void shouldRejectExecuteAfterShutdownNow() {
    CustomThreadPool pool = createTestPool("ShutdownNowTestPool");

    pool.shutdownNow();

    assertThrows(
        RejectedExecutionException.class,
        () ->
            pool.execute(
                () -> {
                  // This task must not be accepted after shutdownNow.
                }));
  }

  /** Verifies that submit() does not accept new tasks after graceful shutdown. */
  @Test
  void shouldRejectSubmitAfterShutdown() {
    CustomThreadPool pool = createTestPool("SubmitAfterShutdownPool");

    pool.shutdown();

    assertThrows(RejectedExecutionException.class, () -> pool.submit(() -> "result"));
  }

  /** Verifies that submit() does not accept new tasks after immediate shutdown. */
  @Test
  void shouldRejectSubmitAfterShutdownNow() {
    CustomThreadPool pool = createTestPool("SubmitAfterShutdownNowPool");

    pool.shutdownNow();

    assertThrows(RejectedExecutionException.class, () -> pool.submit(() -> "result"));
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
