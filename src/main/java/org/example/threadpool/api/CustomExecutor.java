package org.example.threadpool.api;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * Public API of the custom thread pool.
 *
 * <p>In addition to {@link Executor#execute(Runnable)}, the interface provides methods for task
 * submission with results and for pool shutdown control.
 */
public interface CustomExecutor extends Executor {

  /**
   * Executes a {@link Runnable} task.
   *
   * @param command task to execute
   */
  @Override
  void execute(Runnable command);

  /**
   * Submits a {@link Callable} task for execution.
   *
   * @param callable task to execute
   * @param <T> result type
   * @return future representing the pending result
   */
  <T> Future<T> submit(Callable<T> callable);

  /**
   * Starts graceful shutdown.
   *
   * <p>New tasks are no longer accepted, but already accepted tasks may still run.
   */
  void shutdown();

  /**
   * Starts immediate shutdown.
   *
   * <p>New tasks are rejected, pending tasks may be cleared, and worker threads may be interrupted.
   */
  void shutdownNow();
}
