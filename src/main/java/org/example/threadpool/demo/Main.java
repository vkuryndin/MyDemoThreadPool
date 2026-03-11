package org.example.threadpool.demo;

import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.core.CustomThreadPool;
import org.example.threadpool.rejection.RejectPolicy;
import org.example.threadpool.metrics.PoolMetricsSnapshot;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Demo entry point for the custom thread pool project.
 *
 * This class demonstrates:
 * - execute(Runnable)
 * - overload and rejection
 * - graceful shutdown()
 * - submit(Callable) with Future
 * - shutdownNow() with task interruption
 */
public class Main {

    /**
     * Starts all demo scenarios one by one.
     *
     * @param args command-line arguments (not used here)
     * @throws Exception if the main thread is interrupted
     */
    public static void main(String[] args) throws Exception {
        runExecuteAndShutdownDemo();

        Thread.sleep(3000);

        runSubmitDemo();

        Thread.sleep(3000);

        runShutdownNowDemo();

        //added new demo here
        Thread.sleep(3000);

        runConfigurationComparisonDemo();

        System.out.println("\n=== All demos finished ===");
    }

    /**
     * Demonstrates execute(Runnable), overload, rejection, and graceful shutdown.
     *
     * @throws InterruptedException if the main thread is interrupted
     */
    private static void runExecuteAndShutdownDemo() throws InterruptedException {
        System.out.println("\n==============================");
        System.out.println("DEMO 1: execute() + overload + shutdown()");
        System.out.println("==============================");

        CustomThreadPool pool = createDemoPool("ExecuteDemoPool");
        long startNanos = System.nanoTime();

        /**
         * Submit many long-running tasks quickly.
         *
         * This should demonstrate:
         * - normal execution
         * - queue filling
         * - possible pool growth
         * - rejection when overload happens
         */
        for (int i = 1; i <= 12; i++) {
            DemoTask task = new DemoTask("Execute-Task-" + i, 4000);

            try {
                pool.execute(task);
            } catch (RejectedExecutionException e) {
                System.out.println("[Main] Submission failed for " + task + ": " + e.getMessage());
            }
        }

        /**
         * Wait a little so that tasks can start.
         */
        Thread.sleep(7000);

        /**
         * Request graceful shutdown.
         *
         * Already accepted tasks may continue to run.
         */
        System.out.println("[Main] Calling shutdown()");
        pool.shutdown();

        /**
         * Wait so that accepted tasks can finish.
         */
        Thread.sleep(10000);

        long endNanos = System.nanoTime();
        printMetricsSummary("DEMO 1", pool, startNanos, endNanos);
    }

    /**
     * Demonstrates submit(Callable) and Future results.
     *
     * @throws InterruptedException if the main thread is interrupted
     */
    private static void runSubmitDemo() throws InterruptedException {
        System.out.println("\n==============================");
        System.out.println("DEMO 2: submit() + Future");
        System.out.println("==============================");

        CustomThreadPool pool = createDemoPool("SubmitDemoPool");
        long startNanos = System.nanoTime();

        /**
         * Submit several Callable tasks and store the returned futures.
         */
        Future<String> future1 = pool.submit(new DemoCallableTask("Callable-1", 2000));
        Future<String> future2 = pool.submit(new DemoCallableTask("Callable-2", 3000));
        Future<String> future3 = pool.submit(new DemoCallableTask("Callable-3", 1000));

        /**
         * Read results from Future objects.
         *
         * Future.get() blocks until the corresponding task is completed.
         */
        printFutureResult("future1", future1);
        printFutureResult("future2", future2);
        printFutureResult("future3", future3);

        /**
         * Shut down the pool after all results are collected.
         */
        System.out.println("[Main] Calling shutdown()");
        pool.shutdown();

        Thread.sleep(3000);

        long endNanos = System.nanoTime();
        printMetricsSummary("DEMO 2", pool, startNanos, endNanos);
    }

    /**
     * Demonstrates immediate shutdown with interruption of running tasks
     * and clearing of waiting tasks.
     *
     * @throws InterruptedException if the main thread is interrupted
     */
    private static void runShutdownNowDemo() throws InterruptedException {
        System.out.println("\n==============================");
        System.out.println("DEMO 3: shutdownNow()");
        System.out.println("==============================");

        CustomThreadPool pool = createDemoPool("ShutdownNowDemoPool");
        long startNanos = System.nanoTime();

        /**
         * Submit several long-running tasks.
         *
         * Some tasks will start running, others may wait in queues.
         */
        for (int i = 1; i <= 8; i++) {
            DemoTask task = new DemoTask("Interrupt-Task-" + i, 10000);

            try {
                pool.execute(task);
            } catch (RejectedExecutionException e) {
                System.out.println("[Main] Submission failed for " + task + ": " + e.getMessage());
            }
        }

        /**
         * Give workers time to start processing some tasks.
         */
        Thread.sleep(2000);

        /**
         * Request immediate shutdown.
         *
         * Waiting tasks should be removed from queues,
         * and worker threads should be interrupted.
         */
        System.out.println("[Main] Calling shutdownNow()");
        pool.shutdownNow();

        /**
         * Wait to observe interruption logs.
         */
        Thread.sleep(5000);

        long endNanos = System.nanoTime();
        printMetricsSummary("DEMO 3", pool, startNanos, endNanos);
    }

    /**
     * Demonstrates how the same load behaves under different pool configurations.
     *
     * This scenario runs the same number of tasks with the same task duration
     * against several pool configurations and prints metrics for each case.
     *
     * @throws InterruptedException if the main thread is interrupted
     */
    private static void runConfigurationComparisonDemo() throws InterruptedException {
        System.out.println("\n==============================");
        System.out.println("DEMO 4: configuration comparison");
        System.out.println("==============================");

        /**
         * The same workload will be submitted to all configurations.
         *
         * We intentionally use many tasks submitted quickly so that
         * differences in queue capacity and worker scaling become visible.
         */
        int taskCount = 20;
        long taskDurationMillis = 2000;
        long waitAfterShutdownMillis = 10000;

        /**
         * Small configuration:
         * - low core size
         * - low max size
         * - small queue
         *
         * Expected behavior:
         * more rejection and lower peak capacity.
         */
        runSingleConfigurationCase(
                "Config A (small)",
                new PoolConfig(1, 2, 5, TimeUnit.SECONDS, 1, 0),
                taskCount,
                taskDurationMillis,
                waitAfterShutdownMillis
        );

        /**
         * Medium configuration:
         * balanced settings close to the main demo pool.
         */
        runSingleConfigurationCase(
                "Config B (medium)",
                new PoolConfig(2, 4, 5, TimeUnit.SECONDS, 2, 1),
                taskCount,
                taskDurationMillis,
                waitAfterShutdownMillis
        );

        /**
         * Larger configuration:
         * more workers and larger queues.
         *
         * Expected behavior:
         * fewer rejections and higher acceptance capacity.
         */
        runSingleConfigurationCase(
                "Config C (large)",
                new PoolConfig(3, 6, 5, TimeUnit.SECONDS, 4, 1),
                taskCount,
                taskDurationMillis,
                waitAfterShutdownMillis
        );
    }

    /**
     * Creates a demo pool with the default configuration used in main scenarios.
     *
     * @param poolName logical pool name
     * @return a configured CustomThreadPool
     */
    private static CustomThreadPool createDemoPool(String poolName) {
        PoolConfig config = new PoolConfig(
                2,
                4,
                5,
                TimeUnit.SECONDS,
                2,
                1
        );

        return createPool(poolName, config);
    }

    /**
     * Prints the result of a Future in a safe and readable way.
     *
     * @param futureName readable future name
     * @param future the future to read
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
     * Prints a readable summary of pool metrics for one demo scenario.
     *
     * @param demoName readable scenario name
     * @param pool the pool whose metrics should be printed
     * @param startNanos scenario start time in nanoseconds
     * @param endNanos scenario end time in nanoseconds
     */
    private static void printMetricsSummary(String demoName,
                                            CustomThreadPool pool,
                                            long startNanos,
                                            long endNanos) {
        PoolMetricsSnapshot metrics = pool.getMetricsSnapshot();

        double durationSeconds = (endNanos - startNanos) / 1_000_000_000.0;

        double acceptedThroughput = durationSeconds > 0
                ? metrics.getAcceptedTaskCount() / durationSeconds
                : 0.0;

        double completedThroughput = durationSeconds > 0
                ? metrics.getCompletedTaskCount() / durationSeconds
                : 0.0;

        double rejectionRate = metrics.getSubmittedTaskCount() > 0
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
     * Runs one load case for one specific pool configuration.
     *
     * @param caseName readable configuration label
     * @param config pool configuration
     * @param taskCount number of tasks to submit
     * @param taskDurationMillis duration of each task
     * @param waitAfterShutdownMillis wait time after shutdown
     * @throws InterruptedException if the main thread is interrupted
     */
    private static void runSingleConfigurationCase(String caseName,
                                                   PoolConfig config,
                                                   int taskCount,
                                                   long taskDurationMillis,
                                                   long waitAfterShutdownMillis) throws InterruptedException {
        System.out.println("\n----- " + caseName + " -----");
        System.out.println("corePoolSize=" + config.getCorePoolSize()
                + ", maxPoolSize=" + config.getMaxPoolSize()
                + ", queueSize=" + config.getQueueSize()
                + ", minSpareThreads=" + config.getMinSpareThreads());

        CustomThreadPool pool = createPool(caseName.replace(" ", "") + "Pool", config);

        long startNanos = System.nanoTime();

        /**
         * Submit the same workload for every configuration.
         */
        for (int i = 1; i <= taskCount; i++) {
            DemoTask task = new DemoTask(caseName + "-Task-" + i, taskDurationMillis);

            try {
                pool.execute(task);
            } catch (RejectedExecutionException e) {
                System.out.println("[Main] Submission failed for " + task + ": " + e.getMessage());
            }
        }

        /**
         * Graceful shutdown is used so that all accepted tasks
         * have a chance to complete.
         */
        pool.shutdown();

        /**
         * Wait long enough for accepted tasks to finish.
         */
        Thread.sleep(waitAfterShutdownMillis);

        long endNanos = System.nanoTime();

        printMetricsSummary(caseName, pool, startNanos, endNanos);
    }
    /**
     * Creates a pool with a custom configuration.
     *
     * @param poolName logical pool name
     * @param config pool configuration
     * @return configured custom thread pool
     */
    private static CustomThreadPool createPool(String poolName, PoolConfig config) {
        return new CustomThreadPool(
                poolName,
                config,
                new RoundRobinBalancer(),
                new RejectPolicy()
        );
    }
}