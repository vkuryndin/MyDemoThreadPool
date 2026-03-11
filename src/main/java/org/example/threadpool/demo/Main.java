package org.example.threadpool.demo;

import org.example.threadpool.balancer.RoundRobinBalancer;
import org.example.threadpool.config.PoolConfig;
import org.example.threadpool.core.CustomThreadPool;
import org.example.threadpool.rejection.RejectPolicy;

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
    }

    /**
     * Creates a demo pool with the same configuration for all scenarios.
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

        return new CustomThreadPool(
                poolName,
                config,
                new RoundRobinBalancer(),
                new RejectPolicy()
        );
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
}