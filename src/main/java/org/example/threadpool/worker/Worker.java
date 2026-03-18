package org.example.threadpool.worker;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Worker bound to a single queue inside the custom pool.
 *
 * <p>The worker polls tasks from its queue and may terminate after being idle long enough, if the
 * controller allows it.
 */
@SuppressWarnings({"PMD.SystemPrintln", "PMD.UnusedAssignment", "PMD.AvoidDuplicateLiterals"})
public final class Worker implements Runnable {

  /** Pool-side callback interface. */
  private final WorkerController controller;

  /** Queue assigned to this worker. */
  private final BlockingQueue<Runnable> taskQueue;

  /** Idle timeout value. */
  private final long keepAliveTime;

  /** Idle timeout unit. */
  private final TimeUnit timeUnit;

  /** Flag showing whether a task is currently being executed. */
  private volatile boolean busy;

  /**
   * Guards task acceptance and worker shutdown checks.
   *
   * <p>Without this lock, a task could be offered while the worker is already on its way out.
   */
  private final Object queueLock = new Object();

  /** Whether this worker can still accept new tasks. */
  private volatile boolean running;

  private static final int MIN_QUEUE_SIZE = 1;

  private static final long MIN_KEEP_ALIVE_TIME = 0L;

  /**
   * @param controller pool callback interface
   * @param queueSize worker queue capacity
   * @param keepAliveTime idle timeout value
   * @param timeUnit idle timeout unit
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
   * Tries to enqueue a task for this worker.
   *
   * @param task task to enqueue
   * @return {@code true} if the task was accepted
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
   * @return current queue size
   */
  public int getQueueSize() {
    return taskQueue.size();
  }

  /**
   * @return {@code true} if the worker is executing a task
   */
  public boolean isBusy() {
    return busy;
  }

  /**
   * @return {@code true} if there are tasks waiting in the queue
   */
  public boolean hasPendingTasks() {
    return !taskQueue.isEmpty();
  }

  /**
   * Removes tasks that have not started yet.
   *
   * @return number of removed tasks
   */
  public int clearPendingTasks() {
    synchronized (queueLock) {
      int removedCount = taskQueue.size();
      taskQueue.clear();
      return removedCount;
    }
  }

  /** Main worker loop. */
  @Override
  public void run() {
    try {
      while (true) {
        /* Stop immediately on forced shutdown. */
        if (controller.isShutdownNow()) {
          break;
        }

        /* During graceful shutdown, drain the local queue and then exit. */
        if (controller.isShutdown() && taskQueue.isEmpty()) {
          break;
        }

        Runnable task = taskQueue.poll(keepAliveTime, timeUnit);

        /*
         * No task arrived before timeout. Check whether this worker is still needed.
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

        /* The task was taken, but shutdownNow() may have started meanwhile. */
        if (controller.isShutdownNow()) {
          break;
        }

        executeTask(task);
      }
    } catch (InterruptedException e) {
      /* Expected path during shutdownNow() while blocked on poll(). */
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
   * Runs one task and keeps the worker alive even if the task fails.
   *
   * @param task task to execute
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
