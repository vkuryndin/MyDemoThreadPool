package org.example.threadpool.rejection;

import java.util.concurrent.RejectedExecutionException;

/**
 * This rejection policy always rejects the task.
 *
 * <p>It logs the rejection and throws RejectedExecutionException.
 */
@SuppressWarnings("PMD.SystemPrintln")
public class RejectPolicy implements RejectionPolicy {

  /**
   * Rejects the given task.
   *
   * @param task the task that was rejected
   */
  @Override
  public void reject(Runnable task) {
    System.out.println("[Rejected] Task " + task + " was rejected due to overload!");
    throw new RejectedExecutionException("Task was rejected: " + task);
  }
}
