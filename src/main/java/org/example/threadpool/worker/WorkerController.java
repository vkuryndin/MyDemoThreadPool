package org.example.threadpool.worker;

/**
 * This interface allows a Worker to communicate with the thread pool
 * without depending directly on the full pool implementation.
 *
 * The pool will later implement this interface and provide answers
 * about its current state.
 */
public interface WorkerController {

    /**
     * Returns true if graceful shutdown was requested.
     *
     * In graceful shutdown mode, workers should stop accepting new external tasks,
     * but they may continue processing already queued tasks.
     *
     * @return true if shutdown was requested
     */
    boolean isShutdown();

    /**
     * Returns true if immediate shutdown was requested.
     *
     * In this mode, workers should stop as soon as possible.
     *
     * @return true if immediate shutdown was requested
     */
    boolean isShutdownNow();

    /**
     * Decides whether this worker is allowed to stop after idle timeout.
     *
     * Usually the pool will return true only if the current number of workers
     * is greater than corePoolSize.
     *
     * @param worker the worker that asks for permission to stop
     * @return true if the worker may stop
     */
    boolean shouldWorkerStopOnIdle(Worker worker);

    /**
     * Notifies the pool that the worker has terminated.
     *
     * The pool may use this callback to remove the worker from internal collections.
     *
     * @param worker the worker that has terminated
     */
    void onWorkerTerminated(Worker worker);
}