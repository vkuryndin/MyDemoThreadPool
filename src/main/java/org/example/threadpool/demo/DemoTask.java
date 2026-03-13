package org.example.threadpool.demo;

/**
 * A simple demo task used to test the custom thread pool.
 *
 * <p>This task simulates real work by sleeping for a given amount of time. It also prints readable
 * log messages so that we can observe how tasks are executed by different worker threads.
 */
@SuppressWarnings("PMD.SystemPrintln")
public final class DemoTask implements Runnable {

  /** A readable task name used in logs. */
  private final String taskName;

  /** Simulated execution time in milliseconds. */
  private final long workTimeMillis;

  /**
   * Creates a new demo task.
   *
   * @param taskName readable task name
   * @param workTimeMillis simulated execution time in milliseconds
   */
  public DemoTask(String taskName, long workTimeMillis) {
    if (taskName == null || taskName.isBlank()) {
      throw new IllegalArgumentException("taskName cannot be null or blank");
    }

    if (workTimeMillis < 0) {
      throw new IllegalArgumentException("workTimeMillis cannot be negative");
    }

    this.taskName = taskName;
    this.workTimeMillis = workTimeMillis;
  }

  /**
   * Simulates task execution.
   *
   * <p>The task logs its start, sleeps for the configured time, and then logs completion.
   *
   * <p>If the thread is interrupted, the task restores the interrupted flag and prints a log
   * message.
   */
  @Override
  public void run() {
    String threadName = Thread.currentThread().getName();

    System.out.println("[Task] " + taskName + " started on " + threadName);

    try {
      Thread.sleep(workTimeMillis);
      System.out.println("[Task] " + taskName + " completed on " + threadName);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.out.println("[Task] " + taskName + " was interrupted on " + threadName);
    }
  }

  /**
   * Returns a readable task description.
   *
   * <p>This is important because the pool logs task objects directly.
   *
   * @return readable task text
   */
  @Override
  public String toString() {
    return "DemoTask{name='" + taskName + "', workTimeMillis=" + workTimeMillis + "}";
  }
}
