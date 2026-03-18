package org.example.threadpool.rejection;

/** Strategy used when the pool cannot accept another task. */
@FunctionalInterface
public interface RejectionPolicy {

  /**
   * Handles a rejected task.
   *
   * @param task rejected task
   */
  void reject(Runnable task);
}
