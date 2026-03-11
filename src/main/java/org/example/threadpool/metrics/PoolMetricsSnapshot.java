package org.example.threadpool.metrics;

/**
 * Immutable snapshot of the current thread pool metrics.
 *
 * This object is used to safely expose internal counters and current state
 * without giving direct access to mutable fields of the pool.
 */
public class PoolMetricsSnapshot {

    /**
     * Total number of tasks submitted to the pool.
     *
     * This includes both accepted and rejected tasks.
     */
    private final long submittedTaskCount;

    /**
     * Total number of tasks accepted by the pool.
     */
    private final long acceptedTaskCount;

    /**
     * Total number of tasks rejected by the pool.
     */
    private final long rejectedTaskCount;

    /**
     * Total number of tasks whose execution has finished.
     *
     * A task is counted as completed even if it finished with an exception.
     */
    private final long completedTaskCount;

    /**
     * Current number of worker threads known to the pool.
     */
    private final int currentWorkerCount;

    /**
     * Current number of busy workers.
     */
    private final int busyWorkerCount;

    /**
     * Current number of idle workers.
     */
    private final int idleWorkerCount;

    /**
     * Maximum number of workers observed since pool creation.
     */
    private final long peakWorkerCount;

    /**
     * Current total number of pending tasks in all worker queues.
     */
    private final long currentPendingTaskCount;

    /**
     * Maximum total number of pending tasks observed since pool creation.
     */
    private final long peakPendingTaskCount;

    /**
     * Creates a new immutable metrics snapshot.
     *
     * @param submittedTaskCount total submitted tasks
     * @param acceptedTaskCount total accepted tasks
     * @param rejectedTaskCount total rejected tasks
     * @param completedTaskCount total completed tasks
     * @param currentWorkerCount current worker count
     * @param busyWorkerCount current busy worker count
     * @param idleWorkerCount current idle worker count
     * @param peakWorkerCount peak worker count
     * @param currentPendingTaskCount current total pending tasks
     * @param peakPendingTaskCount peak total pending tasks
     */
    public PoolMetricsSnapshot(long submittedTaskCount,
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

    public long getSubmittedTaskCount() {
        return submittedTaskCount;
    }

    public long getAcceptedTaskCount() {
        return acceptedTaskCount;
    }

    public long getRejectedTaskCount() {
        return rejectedTaskCount;
    }

    public long getCompletedTaskCount() {
        return completedTaskCount;
    }

    public int getCurrentWorkerCount() {
        return currentWorkerCount;
    }

    public int getBusyWorkerCount() {
        return busyWorkerCount;
    }

    public int getIdleWorkerCount() {
        return idleWorkerCount;
    }

    public long getPeakWorkerCount() {
        return peakWorkerCount;
    }

    public long getCurrentPendingTaskCount() {
        return currentPendingTaskCount;
    }

    public long getPeakPendingTaskCount() {
        return peakPendingTaskCount;
    }

    /**
     * Returns a readable text representation of the snapshot.
     *
     * This is useful for demo output and debugging.
     *
     * @return formatted metrics text
     */
    @Override
    public String toString() {
        return "PoolMetricsSnapshot{" +
                "submittedTaskCount=" + submittedTaskCount +
                ", acceptedTaskCount=" + acceptedTaskCount +
                ", rejectedTaskCount=" + rejectedTaskCount +
                ", completedTaskCount=" + completedTaskCount +
                ", currentWorkerCount=" + currentWorkerCount +
                ", busyWorkerCount=" + busyWorkerCount +
                ", idleWorkerCount=" + idleWorkerCount +
                ", peakWorkerCount=" + peakWorkerCount +
                ", currentPendingTaskCount=" + currentPendingTaskCount +
                ", peakPendingTaskCount=" + peakPendingTaskCount +
                '}';
    }
}