package org.example.threadpool.api;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * This interface describes the public contract of our custom thread pool.
 *
 * <p>It extends Executor so that the pool can be used in a standard way for running Runnable tasks.
 *
 * <p>We also add submit(), shutdown(), and shutdownNow() methods because the task requires support
 * for both direct task execution and task submission with Future results.
 */
public interface CustomExecutor extends Executor {

  /**
   * Executes a Runnable task.
   *
   * <p>This method is inherited from Executor, but we redeclare it here explicitly to make the
   * contract of our custom executor clearer.
   *
   * @param command the task to execute
   */
  @Override
  void execute(Runnable command);

  /**
   * Submits a Callable task for execution and returns a Future object that can be used to get the
   * result later.
   *
   * @param callable the task that returns a result
   * @param <T> the type of the result
   * @return a Future representing the pending result
   */
  <T> Future<T> submit(Callable<T> callable);

  /**
   * Starts a graceful shutdown.
   *
   * <p>After this call, the pool should stop accepting new tasks, but it should continue processing
   * tasks that were already accepted.
   */
  void shutdown();

  /**
   * Starts an immediate shutdown.
   *
   * <p>After this call, the pool should stop accepting new tasks, clear pending tasks if needed,
   * and interrupt worker threads.
   */
  void shutdownNow();
}
