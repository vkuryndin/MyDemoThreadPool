package org.example.threadpool.worker;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A Worker is a runnable task processor that belongs to the custom thread pool.
 *
 * <p>Each worker has its own task queue and executes tasks from that queue. If the worker stays
 * idle longer than keepAliveTime, it may stop if the pool allows it.
 */
@SuppressWarnings({"PMD.SystemPrintln", "PMD.UnusedAssignment", "PMD.AvoidDuplicateLiterals"})
public final class Worker implements Runnable {

  /**
   * A reference to the pool controller. The worker uses it to check pool state and report
   * termination.
   */
  private final WorkerController controller;

  /** The personal task queue of this worker. */
  private final BlockingQueue<Runnable> taskQueue;

  /** The maximum idle time before this worker may stop. */
  private final long keepAliveTime;

  /** The time unit for keepAliveTime. */
  private final TimeUnit timeUnit;

  /**
   * Shows whether this worker is currently executing a task.
   *
   * <p>We mark it as volatile so that other threads can read the latest value safely.
   */
  private volatile boolean busy;

  /**
   * A private lock that protects task acceptance and worker stop decision.
   *
   * <p>It helps prevent a race when the worker is about to stop while another thread is trying to
   * put a new task into its queue.
   */
  private final Object queueLock = new Object();

  /** Shows whether this worker is still allowed to accept new tasks. */
  private volatile boolean running;

  private static final int MIN_QUEUE_SIZE = 1;

  private static final long MIN_KEEP_ALIVE_TIME = 0L;

  /**
   * Creates a worker with its own bounded task queue.
   *
   * @param controller the object that provides pool state and callbacks
   * @param queueSize the capacity of this worker queue
   * @param keepAliveTime idle timeout value
   * @param timeUnit unit for idle timeout
   */
  public Worker(WorkerController controller, int queueSize, long keepAliveTime, TimeUnit timeUnit) {

    if (controller == null) {
      throw new IllegalArgumentException("controller cannot be null");
    }

    if (queueSize < MIN_QUEUE_SIZE) {
      throw new IllegalArgumentException("queueSize must be at least 1");
    }

    if (keepAliveTime < MIN_KEEP_ALIVE_TIME) {
      throw new IllegalArgumentException("keepAliveTime cannot be negative");
    }

    if (timeUnit == null) {
      throw new IllegalArgumentException("timeUnit cannot be null");
    }

    this.controller = controller;
    this.taskQueue = new ArrayBlockingQueue<>(queueSize);
    this.keepAliveTime = keepAliveTime;
    this.timeUnit = timeUnit;
    this.busy = false;
    this.running = true;
  }

  /**
   * Tries to add a task to this worker queue.
   *
   * <p>The method is synchronized with the worker stop decision, so a task cannot be added to a
   * worker that is already stopping.
   *
   * @param task the task to add
   * @return true if the task was added successfully, false otherwise
   */
  public boolean offerTask(Runnable task) {
    if (task == null) {
      throw new IllegalArgumentException("task cannot be null");
    }

    synchronized (queueLock) {
      if (!running) {
        return false;
      }

      return taskQueue.offer(task);
    }
  }

  /**
   * Returns the current number of tasks waiting in this worker queue.
   *
   * @return queue size
   */
  public int getQueueSize() {
    return taskQueue.size();
  }

  /**
   * Returns true if this worker is currently executing a task.
   *
   * @return true if busy, false otherwise
   */
  public boolean isBusy() {
    return busy;
  }

  /**
   * Returns true if this worker still has queued tasks.
   *
   * @return true if the queue is not empty
   */
  public boolean hasPendingTasks() {
    return !taskQueue.isEmpty();
  }

  /**
   * Removes all tasks that are still waiting in this worker queue.
   *
   * <p>This method does not affect the task that may already be running. It only clears tasks that
   * have not started yet.
   *
   * @return the number of removed tasks
   */
  public int clearPendingTasks() {
    synchronized (queueLock) {
      int removedCount = taskQueue.size();
      taskQueue.clear();
      return removedCount;
    }
  }

  /**
   * Main worker loop.
   *
   * <p>The worker waits for tasks using poll(timeout). This allows it to wake up after
   * keepAliveTime and decide whether it should stop.
   */
  @Override
  public void run() {
    try {
      while (true) {
        /* If immediate shutdown was requested, stop as soon as possible. */
        if (controller.isShutdownNow()) {
          break;
        }

        /*
         * In graceful shutdown mode, the worker should finish only after its queue becomes empty.
         */
        if (controller.isShutdown() && taskQueue.isEmpty()) {
          break;
        }

        Runnable task = taskQueue.poll(keepAliveTime, timeUnit);

        /*
         * If no task was received during the idle timeout, ask the pool whether this worker may
         * stop.
         *
         * <p>The stop decision is synchronized with task acceptance to avoid losing tasks during
         * worker shutdown.
         */
        if (task == null) {
          synchronized (queueLock) {
            if (taskQueue.isEmpty() && controller.shouldWorkerStopOnIdle(this)) {
              running = false;
              System.out.println(
                  "[Worker] " + Thread.currentThread().getName() + " idle timeout, stopping.");
              break;
            }
          }

          continue;
        }

        /* If immediate shutdown started after we received the task, do not execute it. */
        if (controller.isShutdownNow()) {
          break;
        }

        executeTask(task);
      }
    } catch (InterruptedException e) {
      /* The worker may be interrupted during shutdownNow() while waiting on the queue. */
      Thread.currentThread().interrupt();
    } finally {
      synchronized (queueLock) {
        running = false;
      }

      busy = false;
      controller.onWorkerTerminated(this);
      System.out.println("[Worker] " + Thread.currentThread().getName() + " terminated.");
    }
  }

  /**
   * Executes a single task safely.
   *
   * <p>One failed task must not kill the whole worker.
   *
   * @param task the task to execute
   */
  private void executeTask(Runnable task) {
    busy = true;

    try {
      System.out.println("[Worker] " + Thread.currentThread().getName() + " executes " + task);

      task.run();

      System.out.println("[Worker] " + Thread.currentThread().getName() + " finished " + task);
    } catch (RuntimeException e) {
      System.out.println(
          "[Worker] "
              + Thread.currentThread().getName()
              + " task failed: "
              + task
              + ", reason: "
              + e.getMessage());
    } finally {
      busy = false;
    }
  }
}
