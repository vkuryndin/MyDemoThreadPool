package org.example.threadpool.rejection;

import java.util.concurrent.RejectedExecutionException;

/** Rejection policy that always throws. */
@SuppressWarnings("PMD.SystemPrintln")
public class RejectPolicy implements RejectionPolicy {

  /**
   * Logs the rejection and throws an exception.
   *
   * @param task rejected task
   */
  @Override
  public void reject(Runnable task) {
    System.out.println("[Rejected] Task " + task + " was rejected due to overload!");
    throw new RejectedExecutionException("Task was rejected: " + task);
  }
}
