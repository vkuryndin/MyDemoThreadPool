package org.example.threadpool.demo;

import java.util.concurrent.Callable;

/**
 * A simple demo Callable task used to test submit() and Future results.
 *
 * This task simulates work by sleeping for a given amount of time
 * and then returns a readable result string.
 */
public class DemoCallableTask implements Callable<String> {

    /**
     * A readable task name used in logs.
     */
    private final String taskName;

    /**
     * Simulated execution time in milliseconds.
     */
    private final long workTimeMillis;

    /**
     * Creates a new demo callable task.
     *
     * @param taskName readable task name
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
     * Simulates task execution and returns a text result.
     *
     * @return a readable result string
     * @throws Exception if the task is interrupted
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
     * Returns a readable task description.
     *
     * @return readable task text
     */
    @Override
    public String toString() {
        return "DemoCallableTask{name='" + taskName + "', workTimeMillis=" + workTimeMillis + "}";
    }
}