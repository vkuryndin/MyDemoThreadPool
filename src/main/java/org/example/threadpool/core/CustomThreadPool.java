package org.example.threadpool.core;


import org.example.threadpool.api.CustomExecutor;
import org.example.threadpool.balancer.TaskBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.factory.CustomThreadFactory;
import org.example.threadpool.rejection.RejectionPolicy;
import org.example.threadpool.worker.Worker;
import org.example.threadpool.worker.WorkerController;

import org.example.threadpool.metrics.PoolMetricsSnapshot;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.concurrent.FutureTask;

/**
 * This class is the main implementation of the custom thread pool.
 *
 * At this stage, it already:
 * - stores pool configuration and dependencies
 * - creates core workers on startup
 * - tracks workers and their threads
 * - exposes pool state to workers
 *
 * Full task submission logic will be added in the next step.
 */
public class CustomThreadPool implements CustomExecutor, WorkerController {

    /**
     * Pool configuration with all numeric limits and timing settings.
     */
    private final PoolConfig config;

    /**
     * Strategy used to select a target worker queue for a new task.
     */
    private final TaskBalancer taskBalancer;

    /**
     * Policy that defines what to do when the pool cannot accept a task.
     */
    private final RejectionPolicy rejectionPolicy;

    /**
     * Factory used to create worker threads with readable names.
     */
    private final CustomThreadFactory threadFactory;

    /**
     * Active workers currently known to the pool.
     *
     * We use CopyOnWriteArrayList for simplicity and thread-safe iteration.
     * This is acceptable here because workers are created and removed
     * much less often than tasks are processed.
     */
    private final List<Worker> workers = new CopyOnWriteArrayList<>();

    /**
     * Mapping between a worker object and its underlying Java thread.
     *
     * This helps us interrupt worker threads during shutdownNow().
     */
    private final ConcurrentHashMap<Worker, Thread> workerThreads = new ConcurrentHashMap<>();

    /**
     * A private lock object for compound state changes.
     */
    private final Object stateLock = new Object();

    /**
     * True after graceful shutdown was requested.
     */
    private volatile boolean shutdown;

    /**
     * True after immediate shutdown was requested.
     */
    private volatile boolean shutdownNow;

    /**
     * Total number of tasks submitted to the pool.
     *
     * This includes both accepted and rejected tasks.
     */
    private final AtomicLong submittedTaskCount = new AtomicLong(0);

    /**
     * Total number of tasks accepted by the pool.
     */
    private final AtomicLong acceptedTaskCount = new AtomicLong(0);

    /**
     * Total number of tasks rejected by the pool.
     */
    private final AtomicLong rejectedTaskCount = new AtomicLong(0);

    /**
     * Total number of tasks whose execution has finished.
     *
     * A task is counted as completed even if it ended with an exception.
     */
    private final AtomicLong completedTaskCount = new AtomicLong(0);

    /**
     * Peak number of workers observed since pool creation.
     */
    private final AtomicLong peakWorkerCount = new AtomicLong(0);

    /**
     * Peak total number of pending tasks in all worker queues.
     */
    private final AtomicLong peakPendingTaskCount = new AtomicLong(0);

    /**
     * Workers that have already received permission to stop because of idle timeout,
     * but have not fully terminated yet.
     *
     * This reservation prevents multiple workers from stopping simultaneously
     * and shrinking the pool below corePoolSize.
     */
    private final Set<Worker> workersStoppingOnIdle = new HashSet<>();


    /**
     * Internal wrapper around a task that increments the completed counter
     * when task execution finishes.
     *
     * The wrapper delegates toString() to the original task so that logs
     * remain readable.
     */
    private static class TrackedTask implements Runnable {

        /**
         * The original user task.
         */
        private final Runnable delegate;

        /**
         * Counter of completed tasks.
         */
        private final AtomicLong completedTaskCounter;

        /**
         * Creates a new tracked task wrapper.
         *
         * @param delegate the original task
         * @param completedTaskCounter counter for completed tasks
         */
        public TrackedTask(Runnable delegate, AtomicLong completedTaskCounter) {
            this.delegate = delegate;
            this.completedTaskCounter = completedTaskCounter;
        }

        /**
         * Executes the original task and increments the completed counter
         * when execution finishes.
         */
        @Override
        public void run() {
            try {
                delegate.run();
            } finally {
                completedTaskCounter.incrementAndGet();
            }
        }

        /**
         * Delegates readable text representation to the original task.
         *
         * @return original task text
         */
        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    /**
     * Creates a new custom thread pool and immediately starts core workers.
     *
     * @param poolName logical pool name used in worker thread names
     * @param config pool configuration
     * @param taskBalancer task distribution strategy
     * @param rejectionPolicy overload handling policy
     */
    public CustomThreadPool(String poolName,
                            PoolConfig config,
                            TaskBalancer taskBalancer,
                            RejectionPolicy rejectionPolicy) {

        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }

        if (taskBalancer == null) {
            throw new IllegalArgumentException("taskBalancer cannot be null");
        }

        if (rejectionPolicy == null) {
            throw new IllegalArgumentException("rejectionPolicy cannot be null");
        }

        this.config = config;
        this.taskBalancer = taskBalancer;
        this.rejectionPolicy = rejectionPolicy;
        this.threadFactory = new CustomThreadFactory(poolName);

        this.shutdown = false;
        this.shutdownNow = false;

        startCoreWorkers();
    }

    /**
     * Starts the initial number of core workers defined by corePoolSize.
     */
    private void startCoreWorkers() {
        for (int i = 0; i < config.getCorePoolSize(); i++) {
            createAndStartWorker();
        }
    }

    /**
     * Creates a new worker, creates a thread for it, stores references,
     * and starts execution.
     *
     * This method returns the created worker so that the pool may
     * immediately assign a task to it if needed.
     *
     * @return the created worker
     */
    private Worker createAndStartWorker() {
        synchronized (stateLock) {
            Worker worker = new Worker(
                    this,
                    config.getQueueSize(),
                    config.getKeepAliveTime(),
                    config.getTimeUnit()
            );

            Thread thread = threadFactory.newThread(worker);

            workers.add(worker);
            updatePeakWorkerCount(workers.size());
            workerThreads.put(worker, thread);

            thread.start();

            return worker;
        }
    }

    /**
     * Returns the current number of active workers.
     *
     * @return number of workers
     */
    public int getWorkerCount() {
        return workers.size();
    }

    /**
     * Returns the current number of busy workers.
     *
     * A worker is considered busy if it is currently executing a task.
     *
     * @return number of busy workers
     */
    public int getBusyWorkerCount() {
        int busyCount = 0;

        for (Worker worker : workers) {
            if (worker.isBusy()) {
                busyCount++;
            }
        }

        return busyCount;
    }

    /**
     * Returns the current number of idle workers.
     *
     * @return number of idle workers
     */
    public int getIdleWorkerCount() {
        return getWorkerCount() - getBusyWorkerCount();
    }

    /**
     * Returns true if the pool is still allowed to create more workers.
     *
     * @return true if current size is below maxPoolSize
     */
    public boolean canCreateMoreWorkers() {
        return getWorkerCount() < config.getMaxPoolSize();
    }

    /**
     * Tries to keep at least minSpareThreads idle workers available.
     *
     * If the number of idle workers is below the configured minimum
     * and the pool can still grow, new workers are created.
     */
    private void ensureMinSpareWorkers() {
        while (getIdleWorkerCount() < config.getMinSpareThreads() && canCreateMoreWorkers()) {
            createAndStartWorker();
        }
    }

    /**
     * Returns an immutable snapshot of current pool metrics.
     *
     * @return current metrics snapshot
     */
    public PoolMetricsSnapshot getMetricsSnapshot() {
        int currentWorkers = getWorkerCount();
        int busyWorkers = getBusyWorkerCount();
        int idleWorkers = currentWorkers - busyWorkers;
        long currentPending = getTotalPendingTaskCount();

        return new PoolMetricsSnapshot(
                submittedTaskCount.get(),
                acceptedTaskCount.get(),
                rejectedTaskCount.get(),
                completedTaskCount.get(),
                currentWorkers,
                busyWorkers,
                idleWorkers,
                peakWorkerCount.get(),
                currentPending,
                peakPendingTaskCount.get()
        );
    }

    /**
     * Tries to assign a task to one of the existing workers.
     *
     * The balancer selects the starting queue index, and then the pool
     * performs a circular scan over all workers. This gives us a practical
     * fallback if the first selected queue is full.
     *
     * @param command the task to assign
     * @return true if the task was accepted, false otherwise
     */
    private boolean tryAssignTaskToExistingWorker(Runnable command) {
        ArrayList<Worker> snapshot = new ArrayList<>(workers);

        if (snapshot.isEmpty()) {
            return false;
        }

        int startIndex = taskBalancer.selectQueueIndex(snapshot.size());

        for (int offset = 0; offset < snapshot.size(); offset++) {
            int index = (startIndex + offset) % snapshot.size();
            Worker worker = snapshot.get(index);

            if (worker.offerTask(command)) {
                recordTaskAccepted();
                System.out.println("[Pool] Task accepted into queue #" + index + ": " + command);
                return true;
            }
        }

        return false;
    }

    /**
     * Wraps a task so that pool metrics can count completed executions.
     *
     * @param command original task
     * @return wrapped task
     */
    private Runnable wrapWithMetrics(Runnable command) {
        return new TrackedTask(command, completedTaskCount);
    }

    /**
     * Updates the peak worker count if the observed value is greater.
     *
     * @param observedWorkerCount current observed worker count
     */
    private void updatePeakWorkerCount(long observedWorkerCount) {
        while (true) {
            long currentPeak = peakWorkerCount.get();

            if (observedWorkerCount <= currentPeak) {
                return;
            }

            if (peakWorkerCount.compareAndSet(currentPeak, observedWorkerCount)) {
                return;
            }
        }
    }

    /**
     * Returns the current total number of pending tasks in all worker queues.
     *
     * @return total pending task count
     */
    private long getTotalPendingTaskCount() {
        long totalPending = 0;

        for (Worker worker : workers) {
            totalPending += worker.getQueueSize();
        }

        return totalPending;
    }

    /**
     * Updates the peak pending task count based on the current queue load.
     */
    private void updatePeakPendingTaskCount() {
        long observedPending = getTotalPendingTaskCount();

        while (true) {
            long currentPeak = peakPendingTaskCount.get();

            if (observedPending <= currentPeak) {
                return;
            }

            if (peakPendingTaskCount.compareAndSet(currentPeak, observedPending)) {
                return;
            }
        }
    }

    /**
     * Increments the accepted task counter and refreshes peak pending load.
     */
    private void recordTaskAccepted() {
        acceptedTaskCount.incrementAndGet();
        updatePeakPendingTaskCount();
    }

    /**
     * Increments the rejected task counter and applies rejection policy.
     *
     * @param task the rejected task
     */
    private void rejectTask(Runnable task) {
        rejectedTaskCount.incrementAndGet();
        rejectionPolicy.reject(task);
    }


    /**
     * Returns true if graceful shutdown was requested.
     *
     * @return true if shutdown() has been called
     */
    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Returns true if immediate shutdown was requested.
     *
     * @return true if shutdownNow() has been called
     */
    @Override
    public boolean isShutdownNow() {
        return shutdownNow;
    }

    /**
     * Allows a worker to stop after idle timeout only if the effective worker count
     * is still greater than corePoolSize.
     *
     * Effective worker count means:
     * current workers minus workers that have already reserved idle termination.
     *
     * This prevents two or more workers from stopping at the same time
     * and shrinking the pool below corePoolSize.
     *
     * @param worker the worker that asks for permission to stop
     * @return true if this worker may stop
     */
    @Override
    public boolean shouldWorkerStopOnIdle(Worker worker) {
        synchronized (stateLock) {
            int effectiveWorkerCount = workers.size() - workersStoppingOnIdle.size();

            if (effectiveWorkerCount > config.getCorePoolSize()) {
                workersStoppingOnIdle.add(worker);
                return true;
            }

            return false;
        }
    }

    /**
     * Removes the terminated worker from internal collections.
     *
     * @param worker the worker that has terminated
     */
    @Override
    public void onWorkerTerminated(Worker worker) {
        synchronized (stateLock) {
            workersStoppingOnIdle.remove(worker);
            workers.remove(worker);
            workerThreads.remove(worker);
        }
    }

    /**
     * Executes a Runnable task.
     *
     * The pool first checks shutdown state, then tries to keep enough spare workers,
     * then tries to place the task into an existing worker queue.
     * If all existing queues are full, the pool may create a new worker.
     * If that also fails, the rejection policy is applied.
     *
     * @param command the task to execute
     */
    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }

        submittedTaskCount.incrementAndGet();

        Runnable trackedCommand = wrapWithMetrics(command);

        synchronized (stateLock) {
            /**
             * The pool must not accept new tasks after shutdown starts.
             */
            if (shutdown || shutdownNow) {
                rejectTask(trackedCommand);
                return;
            }

            /**
             * Try to maintain the configured number of spare idle workers.
             */
            ensureMinSpareWorkers();

            /**
             * First try to place the task into one of the existing worker queues.
             */
            if (tryAssignTaskToExistingWorker(trackedCommand)) {
                return;
            }

            /**
             * If all existing queues are full, try to grow the pool.
             */
            if (canCreateMoreWorkers()) {
                Worker newWorker = createAndStartWorker();

                if (newWorker.offerTask(trackedCommand)) {
                    int index = workers.indexOf(newWorker);
                    recordTaskAccepted();
                    System.out.println("[Pool] Task accepted into newly created queue #"
                            + index + ": " + trackedCommand);
                    return;
                }
            }

            /**
             * If nothing helped, apply the overload policy.
             */
            rejectTask(trackedCommand);
        }
    }
    /**
     * Submits a Callable task and returns a Future.
     *
     * We wrap the Callable into FutureTask because FutureTask is both:
     * - a Runnable, so it can be passed to execute()
     * - a Future, so the caller can get the result later
     *
     * @param callable the task to submit
     * @param <T> the result type
     * @return Future with task result
     */
    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        if (callable == null) {
            throw new IllegalArgumentException("callable cannot be null");
        }

        FutureTask<T> futureTask = new FutureTask<>(callable);
        execute(futureTask);
        return futureTask;
    }

    /**
     * Starts graceful shutdown.
     *
     * After this call, the pool stops accepting new tasks.
     * Already queued tasks may still be processed by workers.
     */
    @Override
    public void shutdown() {
        synchronized (stateLock) {
            if (shutdownNow) {
                return;
            }

            if (shutdown) {
                return;
            }

            shutdown = true;
            System.out.println("[Pool] Graceful shutdown was requested.");
        }
    }

    /**
     * Starts immediate shutdown.
     *
     * After this call, the pool stops accepting new tasks,
     * clears all pending tasks from worker queues,
     * and interrupts worker threads.
     */
    @Override
    public void shutdownNow() {
        synchronized (stateLock) {
            if (shutdownNow) {
                return;
            }

            shutdown = true;
            shutdownNow = true;

            System.out.println("[Pool] Immediate shutdown was requested.");

            int removedTasks = 0;

            for (Worker worker : workers) {
                removedTasks += worker.clearPendingTasks();
            }

            System.out.println("[Pool] Cleared " + removedTasks + " pending task(s) from worker queues.");

            for (Thread thread : workerThreads.values()) {
                thread.interrupt();
            }
        }
    }
}