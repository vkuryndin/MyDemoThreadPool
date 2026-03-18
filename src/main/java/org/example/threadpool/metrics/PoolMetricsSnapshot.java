package org.example.threadpool.metrics;

/** Immutable view of current pool metrics. */
public class PoolMetricsSnapshot {

  /** Total number of submitted tasks, including rejected ones. */
  private final long submittedTaskCount;

  /** Total number of accepted tasks. */
  private final long acceptedTaskCount;

  /** Total number of rejected tasks. */
  private final long rejectedTaskCount;

  /** Total number of finished tasks. */
  private final long completedTaskCount;

  /** Current number of registered workers. */
  private final int currentWorkerCount;

  /** Current number of workers running a task. */
  private final int busyWorkerCount;

  /** Current number of idle workers. */
  private final int idleWorkerCount;

  /** Maximum number of workers seen since pool start. */
  private final long peakWorkerCount;

  /** Current total queue size across all workers. */
  private final long currentPendingTaskCount;

  /** Highest observed total queue size across all workers. */
  private final long peakPendingTaskCount;

  /**
   * @param submittedTaskCount total submitted tasks
   * @param acceptedTaskCount total accepted tasks
   * @param rejectedTaskCount total rejected tasks
   * @param completedTaskCount total finished tasks
   * @param currentWorkerCount current worker count
   * @param busyWorkerCount current busy worker count
   * @param idleWorkerCount current idle worker count
   * @param peakWorkerCount peak worker count
   * @param currentPendingTaskCount current pending task count
   * @param peakPendingTaskCount peak pending task count
   */
  public PoolMetricsSnapshot(
      long submittedTaskCount,
      long acceptedTaskCount,
      long rejectedTaskCount,
      long completedTaskCount,
      int currentWorkerCount,
      int busyWorkerCount,
      int idleWorkerCount,
      long peakWorkerCount,
      long currentPendingTaskCount,
      long peakPendingTaskCount) {
    this.submittedTaskCount = submittedTaskCount;
    this.acceptedTaskCount = acceptedTaskCount;
    this.rejectedTaskCount = rejectedTaskCount;
    this.completedTaskCount = completedTaskCount;
    this.currentWorkerCount = currentWorkerCount;
    this.busyWorkerCount = busyWorkerCount;
    this.idleWorkerCount = idleWorkerCount;
    this.peakWorkerCount = peakWorkerCount;
    this.currentPendingTaskCount = currentPendingTaskCount;
    this.peakPendingTaskCount = peakPendingTaskCount;
  }

  /**
   * @return submitted task count
   */
  public long getSubmittedTaskCount() {
    return submittedTaskCount;
  }

  /**
   * @return accepted task count
   */
  public long getAcceptedTaskCount() {
    return acceptedTaskCount;
  }

  /**
   * @return rejected task count
   */
  public long getRejectedTaskCount() {
    return rejectedTaskCount;
  }

  /**
   * @return completed task count
   */
  public long getCompletedTaskCount() {
    return completedTaskCount;
  }

  /**
   * @return current worker count
   */
  public int getCurrentWorkerCount() {
    return currentWorkerCount;
  }

  /**
   * @return busy worker count
   */
  public int getBusyWorkerCount() {
    return busyWorkerCount;
  }

  /**
   * @return idle worker count
   */
  public int getIdleWorkerCount() {
    return idleWorkerCount;
  }

  /**
   * @return peak worker count
   */
  public long getPeakWorkerCount() {
    return peakWorkerCount;
  }

  /**
   * @return current pending task count
   */
  public long getCurrentPendingTaskCount() {
    return currentPendingTaskCount;
  }

  /**
   * @return peak pending task count
   */
  public long getPeakPendingTaskCount() {
    return peakPendingTaskCount;
  }

  /**
   * @return snapshot as text
   */
  @Override
  public String toString() {
    return "PoolMetricsSnapshot{"
        + "submittedTaskCount="
        + submittedTaskCount
        + ", acceptedTaskCount="
        + acceptedTaskCount
        + ", rejectedTaskCount="
        + rejectedTaskCount
        + ", completedTaskCount="
        + completedTaskCount
        + ", currentWorkerCount="
        + currentWorkerCount
        + ", busyWorkerCount="
        + busyWorkerCount
        + ", idleWorkerCount="
        + idleWorkerCount
        + ", peakWorkerCount="
        + peakWorkerCount
        + ", currentPendingTaskCount="
        + currentPendingTaskCount
        + ", peakPendingTaskCount="
        + peakPendingTaskCount
        + '}';
  }
}
