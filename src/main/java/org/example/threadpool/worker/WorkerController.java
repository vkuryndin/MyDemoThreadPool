package org.example.threadpool.worker;

/** Narrow contract between a worker and the pool. */
public interface WorkerController {

  /**
   * @return {@code true} if graceful shutdown was requested
   */
  boolean isShutdown();

  /**
   * @return {@code true} if forced shutdown was requested
   */
  boolean isShutdownNow();

  /**
   * Called by an idle worker before it stops itself.
   *
   * @param worker worker asking for permission to stop
   * @return {@code true} if the worker may terminate
   */
  boolean shouldWorkerStopOnIdle(Worker worker);

  /**
   * Notification sent when a worker exits.
   *
   * @param worker terminated worker
   */
  void onWorkerTerminated(Worker worker);
}
