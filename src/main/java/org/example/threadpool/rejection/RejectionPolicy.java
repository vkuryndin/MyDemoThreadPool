package org.example.threadpool.rejection;

/**
 * This interface defines what should happen when the thread pool cannot accept a new task.
 *
 * <p>Different implementations may reject the task, run it in the caller thread, discard it, or use
 * any other custom strategy.
 */
@FunctionalInterface
public interface RejectionPolicy {

  /**
   * Handles a task that cannot be accepted by the pool.
   *
   * @param task the task that was rejected
   */
  void reject(Runnable task);
}
