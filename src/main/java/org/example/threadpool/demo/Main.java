package org.example.threadpool.demo;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.core.CustomThreadPool;
import org.example.threadpool.metrics.PoolMetricsSnapshot;
import org.example.threadpool.rejection.RejectPolicy;

/**
 * Entry point for thread pool demo runs.
 *
 * <p>Contains a few standalone scenarios: basic execution, callable submission, immediate shutdown,
 * and a comparison of several pool configurations.
 */
@SuppressWarnings("PMD.SystemPrintln")
public class Main {

  private static final String SECTION_LINE = "==============================";

  private static final String SECTION_HEADER_PREFIX = "\n==============================";

  /**
   * Runs all demo scenarios.
   *
   * @param args command-line arguments
   * @throws Exception if the current thread is interrupted
   */
  public static void main(String[] args) throws Exception {
    runExecuteAndShutdownDemo();

    Thread.sleep(3000);

    runSubmitDemo();

    Thread.sleep(3000);

    runShutdownNowDemo();

    // pause before the configuration comparison block
    Thread.sleep(3000);

    runConfigurationComparisonDemo();

    System.out.println("=== All demos finished ===");
  }

  /**
   * Shows normal task submission, overload handling, and graceful shutdown.
   *
   * @throws InterruptedException if the current thread is interrupted
   */
  private static void runExecuteAndShutdownDemo() throws InterruptedException {
    System.out.println(SECTION_HEADER_PREFIX);
    System.out.println("DEMO 1: execute() + overload + shutdown()");
    System.out.println(SECTION_LINE);

    CustomThreadPool pool = createDemoPool("ExecuteDemoPool");
    long startNanos = System.nanoTime();

    /* Push a burst of long tasks into the pool. */
    for (int i = 1; i <= 12; i++) {
      DemoTask task = new DemoTask("Execute-Task-" + i, 4000);

      try {
        pool.execute(task);
      } catch (RejectedExecutionException e) {
        System.out.println("[Main] Submission failed for " + task + ": " + e.getMessage());
      }
    }

    /* Let the workers pick up part of the load. */
    Thread.sleep(7000);

    /* Stop accepting new work, but let accepted tasks finish. */
    System.out.println("[Main] Calling shutdown()");
    pool.shutdown();

    /* Leave enough time for the accepted tasks to complete. */
    Thread.sleep(10000);

    long endNanos = System.nanoTime();
    printMetricsSummary("DEMO 1", pool, startNanos, endNanos);
  }

  /**
   * Shows {@code submit()} and reading results through {@link Future}.
   *
   * @throws InterruptedException if the current thread is interrupted
   */
  private static void runSubmitDemo() throws InterruptedException {
    System.out.println(SECTION_HEADER_PREFIX);
    System.out.println("DEMO 2: submit() + Future");
    System.out.println(SECTION_LINE);

    CustomThreadPool pool = createDemoPool("SubmitDemoPool");
    long startNanos = System.nanoTime();

    /* Keep futures so the results can be read later. */
    Future<String> future1 = pool.submit(new DemoCallableTask("Callable-1", 2000));
    Future<String> future2 = pool.submit(new DemoCallableTask("Callable-2", 3000));
    Future<String> future3 = pool.submit(new DemoCallableTask("Callable-3", 1000));

    /* get() waits until the corresponding task is done. */
    printFutureResult("future1", future1);
    printFutureResult("future2", future2);
    printFutureResult("future3", future3);

    /* Shut the pool down after all futures are resolved. */
    System.out.println("[Main] Calling shutdown()");
    pool.shutdown();

    Thread.sleep(3000);

    long endNanos = System.nanoTime();
    printMetricsSummary("DEMO 2", pool, startNanos, endNanos);
  }

  /**
   * Shows forced shutdown with interruption of active workers and cleanup of waiting tasks.
   *
   * @throws InterruptedException if the current thread is interrupted
   */
  private static void runShutdownNowDemo() throws InterruptedException {
    System.out.println(SECTION_HEADER_PREFIX);
    System.out.println("DEMO 3: shutdownNow()");
    System.out.println(SECTION_LINE);

    CustomThreadPool pool = createDemoPool("ShutdownNowDemoPool");
    long startNanos = System.nanoTime();

    /* Some tasks should start, the rest will remain queued. */
    for (int i = 1; i <= 8; i++) {
      DemoTask task = new DemoTask("Interrupt-Task-" + i, 10000);

      try {
        pool.execute(task);
      } catch (RejectedExecutionException e) {
        System.out.println("[Main] Submission failed for " + task + ": " + e.getMessage());
      }
    }

    /* Give the pool a moment to start processing. */
    Thread.sleep(2000);

    /* Force shutdown and interrupt running workers. */
    System.out.println("[Main] Calling shutdownNow()");
    pool.shutdownNow();

    /* Keep the pause so interruption messages can be seen in the log. */
    Thread.sleep(5000);

    long endNanos = System.nanoTime();
    printMetricsSummary("DEMO 3", pool, startNanos, endNanos);
  }

  /**
   * Runs the same workload against several pool configurations.
   *
   * @throws InterruptedException if the current thread is interrupted
   */
  private static void runConfigurationComparisonDemo() throws InterruptedException {
    System.out.println(SECTION_HEADER_PREFIX);
    System.out.println("DEMO 4: configuration comparison");
    System.out.println(SECTION_LINE);

    /* Use identical load parameters for every configuration. */
    int taskCount = 20;
    long taskDurationMillis = 2000;
    long waitAfterShutdownMillis = 10000;

    /* Small pool: low ceiling and short queues. */
    runSingleConfigurationCase(
            "Config A (small)",
            new PoolConfig(1, 2, 5, TimeUnit.SECONDS, 1, 0),
            taskCount,
            taskDurationMillis,
            waitAfterShutdownMillis);

    /* Medium pool: close to the default demo setup. */
    runSingleConfigurationCase(
            "Config B (medium)",
            new PoolConfig(2, 4, 5, TimeUnit.SECONDS, 2, 1),
            taskCount,
            taskDurationMillis,
            waitAfterShutdownMillis);

    /* Larger pool: more workers and more room in queues. */
    runSingleConfigurationCase(
            "Config C (large)",
            new PoolConfig(3, 6, 5, TimeUnit.SECONDS, 4, 1),
            taskCount,
            taskDurationMillis,
            waitAfterShutdownMillis);
  }

  /**
   * Builds the default pool used by the main demo scenarios.
   *
   * @param poolName logical pool name
   * @return configured pool instance
   */
  private static CustomThreadPool createDemoPool(String poolName) {
    PoolConfig config = new PoolConfig(2, 4, 5, TimeUnit.SECONDS, 2, 1);

    return createPool(poolName, config);
  }

  /**
   * Prints a single future result without aborting the whole demo on failure.
   *
   * @param futureName name shown in the output
   * @param future future to read
   */
  private static void printFutureResult(String futureName, Future<String> future) {
    try {
      String result = future.get();
      System.out.println("[Main] " + futureName + " result: " + result);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.out.println("[Main] " + futureName + " was interrupted while waiting for result.");
    } catch (ExecutionException e) {
      System.out.println("[Main] " + futureName + " failed: " + e.getCause());
    }
  }

  /**
   * Prints a short metrics report for one demo block.
   *
   * @param demoName scenario name
   * @param pool pool instance
   * @param startNanos scenario start time
   * @param endNanos scenario end time
   */
  private static void printMetricsSummary(
          String demoName, CustomThreadPool pool, long startNanos, long endNanos) {
    PoolMetricsSnapshot metrics = pool.getMetricsSnapshot();

    double durationSeconds = (endNanos - startNanos) / 1_000_000_000.0;

    double acceptedThroughput =
            durationSeconds > 0 ? metrics.getAcceptedTaskCount() / durationSeconds : 0.0;

    double completedThroughput =
            durationSeconds > 0 ? metrics.getCompletedTaskCount() / durationSeconds : 0.0;

    double rejectionRate =
            metrics.getSubmittedTaskCount() > 0
                    ? (metrics.getRejectedTaskCount() * 100.0) / metrics.getSubmittedTaskCount()
                    : 0.0;

    System.out.println("\n----- " + demoName + " metrics summary -----");
    System.out.println("Submitted tasks      : " + metrics.getSubmittedTaskCount());
    System.out.println("Accepted tasks       : " + metrics.getAcceptedTaskCount());
    System.out.println("Rejected tasks       : " + metrics.getRejectedTaskCount());
    System.out.println("Completed tasks      : " + metrics.getCompletedTaskCount());
    System.out.println("Current workers      : " + metrics.getCurrentWorkerCount());
    System.out.println("Busy workers         : " + metrics.getBusyWorkerCount());
    System.out.println("Idle workers         : " + metrics.getIdleWorkerCount());
    System.out.println("Peak workers         : " + metrics.getPeakWorkerCount());
    System.out.println("Current pending tasks: " + metrics.getCurrentPendingTaskCount());
    System.out.println("Peak pending tasks   : " + metrics.getPeakPendingTaskCount());
    System.out.printf("Duration (seconds)   : %.3f%n", durationSeconds);
    System.out.printf("Accepted throughput  : %.3f tasks/sec%n", acceptedThroughput);
    System.out.printf("Completed throughput : %.3f tasks/sec%n", completedThroughput);
    System.out.printf("Rejection rate       : %.2f%%%n", rejectionRate);
  }

  /**
   * Runs one workload against one configuration.
   *
   * @param caseName label shown in the output
   * @param config pool configuration
   * @param taskCount number of tasks to submit
   * @param taskDurationMillis duration of each task
   * @param waitAfterShutdownMillis pause after shutdown
   * @throws InterruptedException if the current thread is interrupted
   */
  private static void runSingleConfigurationCase(
      String caseName,
      PoolConfig config,
      int taskCount,
      long taskDurationMillis,
      long waitAfterShutdownMillis)
      throws InterruptedException {
    System.out.println("\n----- " + caseName + " -----");
    System.out.println(
        "corePoolSize="
            + config.getCorePoolSize()
            + ", maxPoolSize="
            + config.getMaxPoolSize()
            + ", queueSize="
            + config.getQueueSize()
            + ", minSpareThreads="
            + config.getMinSpareThreads());

    CustomThreadPool pool = createPool(caseName.replace(" ", "") + "Pool", config);

    long startNanos = System.nanoTime();

    /* Submit the same batch for each configuration. */
    for (int i = 1; i <= taskCount; i++) {
      DemoTask task = new DemoTask(caseName + "-Task-" + i, taskDurationMillis);

      try {
        pool.execute(task);
      } catch (RejectedExecutionException e) {
        System.out.println("[Main] Submission failed for " + task + ": " + e.getMessage());
      }
    }

    /* Use graceful shutdown so accepted tasks can finish normally. */
    pool.shutdown();

    /* Wait for the accepted tasks to drain. */
    Thread.sleep(waitAfterShutdownMillis);

    long endNanos = System.nanoTime();

    printMetricsSummary(caseName, pool, startNanos, endNanos);
  }

  /**
   * Builds a pool with the given name and configuration.
   *
   * @param poolName logical pool name
   * @param config pool configuration
   * @return configured pool instance
   */
  private static CustomThreadPool createPool(String poolName, PoolConfig config) {
    return new CustomThreadPool(poolName, config, new RoundRobinBalancer(), new RejectPolicy());
  }
}
