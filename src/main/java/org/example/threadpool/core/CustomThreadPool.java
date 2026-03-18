package org.example.threadpool.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;
import org.example.threadpool.api.CustomExecutor;
import org.example.threadpool.balancer.TaskBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.factory.CustomThreadFactory;
import org.example.threadpool.metrics.PoolMetricsSnapshot;
import org.example.threadpool.rejection.RejectionPolicy;
import org.example.threadpool.worker.Worker;
import org.example.threadpool.worker.WorkerController;

/**
 * Main implementation of the custom thread pool.
 *
 * <p>This class coordinates worker lifecycle, task submission, task distribution, overload
 * handling, runtime metrics, and shutdown behavior.
 *
 * <p>The pool starts with the configured number of core workers and may create additional workers
 * up to the configured maximum when load increases.
 */
@SuppressWarnings("PMD.SystemPrintln")
public final class CustomThreadPool implements CustomExecutor, WorkerController {

  /** Pool configuration with size limits, queue settings, and timing parameters. */
  private final PoolConfig config;

  /** Strategy used to choose the starting queue during task assignment. */
  private final TaskBalancer taskBalancer;

  /** Policy applied when the pool cannot accept a task. */
  private final RejectionPolicy rejectionPolicy;

  /** Factory used to create worker threads with readable names. */
  private final CustomThreadFactory threadFactory;

  /**
   * Active workers currently managed by the pool.
   *
   * <p>{@link CopyOnWriteArrayList} is used here to simplify thread-safe iteration over the worker
   * collection.
   */
  private final List<Worker> workers = new CopyOnWriteArrayList<>();

  /**
   * Mapping between workers and their underlying Java threads.
   *
   * <p>This is used when worker threads need to be interrupted during immediate shutdown.
   */
  private final Map<Worker, Thread> workerThreads = new ConcurrentHashMap<>();

  /** Lock used for compound state changes inside the pool. */
  private final Object stateLock = new Object();

  /** Becomes {@code true} after graceful shutdown is requested. */
  private volatile boolean shutdownRequested;

  /** Becomes {@code true} after immediate shutdown is requested. */
  private volatile boolean shutdownNowRequested;

  /**
   * Total number of tasks submitted to the pool.
   *
   * <p>This counter includes both accepted and rejected tasks.
   */
  private final AtomicLong submittedTaskCount = new AtomicLong(0);

  /** Total number of tasks accepted by the pool. */
  private final AtomicLong acceptedTaskCount = new AtomicLong(0);

  /** Total number of tasks rejected by the pool. */
  private final AtomicLong rejectedTaskCount = new AtomicLong(0);

  /**
   * Total number of tasks whose execution has finished.
   *
   * <p>A task is counted as completed even if it ends with an exception.
   */
  private final AtomicLong completedTaskCount = new AtomicLong(0);

  /** Highest worker count observed since pool creation. */
  private final AtomicLong peakWorkerCount = new AtomicLong(0);

  /** Highest total number of pending tasks observed since pool creation. */
  private final AtomicLong peakPendingTaskCount = new AtomicLong(0);

  /**
   * Workers that have already been allowed to stop after idle timeout but have not fully terminated
   * yet.
   *
   * <p>This reservation prevents several workers from stopping at the same time and shrinking the
   * pool below {@code corePoolSize}.
   */
  private final Set<Worker> workersStoppingOnIdle = new HashSet<>();

  /**
   * Runnable wrapper that increments the completed-task counter when task execution finishes.
   *
   * <p>The wrapper delegates {@code toString()} to the original task so that log messages remain
   * readable.
   */
  private static class TrackedTask implements Runnable {

    /** Original user task. */
    private final Runnable delegate;

    /** Counter for finished tasks. */
    private final AtomicLong completedTaskCounter;

    /**
     * Creates a new tracked task wrapper.
     *
     * @param delegate original task
     * @param completedTaskCounter counter of completed tasks
     */
    public TrackedTask(Runnable delegate, AtomicLong completedTaskCounter) {
      this.delegate = delegate;
      this.completedTaskCounter = completedTaskCounter;
    }

    /** Executes the original task and updates the completion counter afterward. */
    @Override
    public void run() {
      try {
        delegate.run();
      } finally {
        completedTaskCounter.incrementAndGet();
      }
    }

    /**
     * Returns the string representation of the original task.
     *
     * @return original task text
     */
    @Override
    public String toString() {
      return delegate.toString();
    }
  }

  /**
   * Creates a new custom thread pool and starts core workers immediately.
   *
   * @param poolName logical pool name used in worker thread names
   * @param config pool configuration
   * @param taskBalancer task distribution strategy
   * @param rejectionPolicy overload handling policy
   */
  public CustomThreadPool(
      String poolName,
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

    this.shutdownRequested = false;
    this.shutdownNowRequested = false;

    startCoreWorkers();
  }

  /** Starts the initial set of workers defined by {@code corePoolSize}. */
  private void startCoreWorkers() {
    for (int i = 0; i < config.getCorePoolSize(); i++) {
      createAndStartWorker();
    }
  }

  /**
   * Creates a worker, creates a thread for it, stores internal references, and starts the thread.
   *
   * <p>The created worker is returned so that the pool may immediately try to assign a task to it
   * if necessary.
   *
   * @return created worker
   */
  private Worker createAndStartWorker() {
    synchronized (stateLock) {
      Worker worker =
          new Worker(this, config.getQueueSize(), config.getKeepAliveTime(), config.getTimeUnit());

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
   * @return worker count
   */
  public int getWorkerCount() {
    return workers.size();
  }

  /**
   * Returns the current number of busy workers.
   *
   * <p>A worker is considered busy while it is executing a task.
   *
   * @return busy worker count
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
   * @return idle worker count
   */
  public int getIdleWorkerCount() {
    return getWorkerCount() - getBusyWorkerCount();
  }

  /**
   * Returns {@code true} if the pool is still allowed to create more workers.
   *
   * @return {@code true} when the current worker count is below {@code maxPoolSize}
   */
  public boolean canCreateMoreWorkers() {
    return getWorkerCount() < config.getMaxPoolSize();
  }

  /**
   * Ensures that the pool tries to keep the configured number of spare idle workers.
   *
   * <p>If the number of idle workers is below {@code minSpareThreads} and the pool can still grow,
   * additional workers are created.
   */
  private void ensureMinSpareWorkers() {
    while (getIdleWorkerCount() < config.getMinSpareThreads() && canCreateMoreWorkers()) {
      createAndStartWorker();
    }
  }

  /**
   * Returns an immutable snapshot of current runtime metrics.
   *
   * @return metrics snapshot
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
        peakPendingTaskCount.get());
  }

  /**
   * Tries to assign a task to one of the existing workers.
   *
   * <p>The balancer provides the starting queue index, and the pool then performs a circular scan
   * over all current workers.
   *
   * @param command task to assign
   * @return {@code true} if the task was accepted, {@code false} otherwise
   */
  private boolean tryAssignTaskToExistingWorker(Runnable command) {
    List<Worker> snapshot = new ArrayList<>(workers);

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
   * Wraps a task so that task completion is reflected in pool metrics.
   *
   * @param command original task
   * @return wrapped task
   */
  private Runnable wrapWithMetrics(Runnable command) {
    return new TrackedTask(command, completedTaskCount);
  }

  /**
   * Updates the peak worker count if the currently observed value is greater.
   *
   * @param observedWorkerCount currently observed worker count
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
   * Returns the current total number of tasks waiting in all worker queues.
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

  /** Updates the peak pending-task counter using the current queue load. */
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

  /** Increments the accepted-task counter and refreshes peak pending load. */
  private void recordTaskAccepted() {
    acceptedTaskCount.incrementAndGet();
    updatePeakPendingTaskCount();
  }

  /**
   * Increments the rejected-task counter and applies the rejection policy.
   *
   * @param task rejected task
   */
  private void rejectTask(Runnable task) {
    rejectedTaskCount.incrementAndGet();
    rejectionPolicy.reject(task);
  }

  /**
   * Returns {@code true} if graceful shutdown was requested.
   *
   * @return {@code true} if {@link #shutdown()} has been called
   */
  @Override
  public boolean isShutdown() {
    return shutdownRequested;
  }

  /**
   * Returns {@code true} if immediate shutdown was requested.
   *
   * @return {@code true} if {@link #shutdownNow()} has been called
   */
  @Override
  public boolean isShutdownNow() {
    return shutdownNowRequested;
  }

  /**
   * Allows a worker to stop after idle timeout only if the effective worker count is still greater
   * than {@code corePoolSize}.
   *
   * <p>The effective worker count is calculated as the current worker count minus the number of
   * workers that have already reserved idle termination.
   *
   * <p>This prevents multiple workers from stopping at the same time and shrinking the pool below
   * the configured core size.
   *
   * @param worker worker requesting permission to stop
   * @return {@code true} if this worker may stop
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
   * Removes a terminated worker from internal collections.
   *
   * @param worker terminated worker
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
   * Executes a {@link Runnable} task.
   *
   * <p>The pool first checks its shutdown state, then tries to maintain the configured number of
   * spare idle workers, then attempts to place the task into an existing queue. If all current
   * queues are full, the pool may create a new worker. If the task still cannot be accepted, the
   * rejection policy is applied.
   *
   * @param command task to execute
   */
  @Override
  public void execute(Runnable command) {
    if (command == null) {
      throw new IllegalArgumentException("command cannot be null");
    }

    submittedTaskCount.incrementAndGet();

    Runnable trackedCommand = wrapWithMetrics(command);

    synchronized (stateLock) {
      // The pool must not accept new tasks after shutdown starts.
      if (shutdownRequested || shutdownNowRequested) {
        rejectTask(trackedCommand);
        return;
      }

      // Try to maintain the configured number of spare idle workers.
      ensureMinSpareWorkers();

      // First try to place the task into one of the existing worker queues.
      if (tryAssignTaskToExistingWorker(trackedCommand)) {
        return;
      }

      // If all current queues are full, try to grow the pool.
      if (canCreateMoreWorkers()) {
        Worker newWorker = createAndStartWorker();

        if (newWorker.offerTask(trackedCommand)) {
          int index = workers.indexOf(newWorker);
          recordTaskAccepted();
          System.out.println(
              "[Pool] Task accepted into newly created queue #" + index + ": " + trackedCommand);
          return;
        }
      }

      // If the task still cannot be accepted, apply the overload policy.
      rejectTask(trackedCommand);
    }
  }

  /**
   * Submits a {@link Callable} task and returns a {@link Future}.
   *
   * <p>The callable is wrapped into {@link FutureTask} so that it can be executed through the same
   * internal pipeline as {@link Runnable} tasks while still exposing a result to the caller.
   *
   * @param callable task to submit
   * @param <T> result type
   * @return future representing the pending result
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
   * <p>After this call, the pool stops accepting new tasks. Tasks that have already been accepted
   * may still be processed by workers.
   */
  @Override
  public void shutdown() {
    synchronized (stateLock) {
      if (shutdownNowRequested) {
        return;
      }

      if (shutdownRequested) {
        return;
      }

      shutdownRequested = true;
      System.out.println("[Pool] Graceful shutdown was requested.");
    }
  }

  /**
   * Starts immediate shutdown.
   *
   * <p>After this call, the pool stops accepting new tasks, clears pending tasks from worker
   * queues, and interrupts worker threads.
   */
  @Override
  public void shutdownNow() {
    synchronized (stateLock) {
      if (shutdownNowRequested) {
        return;
      }

      shutdownRequested = true;
      shutdownNowRequested = true;

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
