package org.example.threadpool.demo;

/**
 * Test runnable used by the demo.
 *
 * <p>Sleeps for the configured time and writes basic start/finish messages to the console.
 */
@SuppressWarnings("PMD.SystemPrintln")
public final class DemoTask implements Runnable {

  /** Task name shown in the log output. */
  private final String taskName;

  /** Artificial execution time. */
  private final long workTimeMillis;

  /**
   * @param taskName task name for log output
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

  /** Runs the demo task. */
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
   * @return string form used in logs
   */
  @Override
  public String toString() {
    return "DemoTask{name='" + taskName + "', workTimeMillis=" + workTimeMillis + "}";
  }
}
