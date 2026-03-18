package org.example.threadpool.demo;

import java.util.concurrent.Callable;

/**
 * Test callable used in demo scenarios.
 *
 * <p>Sleeps for the configured time and then returns a result string.
 */
@SuppressWarnings("PMD.SystemPrintln")
public final class DemoCallableTask implements Callable<String> {

  /** Task name shown in the log output. */
  private final String taskName;

  /** Artificial execution time. */
  private final long workTimeMillis;

  /**
   * @param taskName task name for log output
   * @param workTimeMillis simulated execution time in milliseconds
   */
  public DemoCallableTask(String taskName, long workTimeMillis) {
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
   * Runs the task body.
   *
   * @return task result
   * @throws Exception if execution is interrupted
   */
  @Override
  public String call() throws Exception {
    String threadName = Thread.currentThread().getName();

    System.out.println("[CallableTask] " + taskName + " started on " + threadName);

    try {
      Thread.sleep(workTimeMillis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.out.println("[CallableTask] " + taskName + " was interrupted on " + threadName);
      throw e;
    }

    String result = "Result of " + taskName + " from " + threadName;
    System.out.println("[CallableTask] " + taskName + " completed on " + threadName);

    return result;
  }

  /**
   * @return string form used in logs
   */
  @Override
  public String toString() {
    return "DemoCallableTask{name='" + taskName + "', workTimeMillis=" + workTimeMillis + "}";
  }
}
