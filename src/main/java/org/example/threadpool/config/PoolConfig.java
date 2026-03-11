package org.example.threadpool.config;

import java.util.concurrent.TimeUnit;

/**
 * This class stores all configuration parameters of the custom thread pool.
 *
 * We keep pool settings in a separate object to make the code cleaner
 * and to avoid passing many constructor arguments one by one.
 */
public class PoolConfig {

    /**
     * The minimum number of worker threads that should exist in the pool.
     */
    private final int corePoolSize;

    /**
     * The maximum number of worker threads that can exist in the pool.
     */
    private final int maxPoolSize;

    /**
     * The amount of time that an idle worker can stay alive
     * before it is allowed to stop.
     */
    private final long keepAliveTime;

    /**
     * The time unit for keepAliveTime.
     */
    private final TimeUnit timeUnit;

    /**
     * The capacity of a single worker queue.
     *
     * In our design, every worker has its own queue,
     * so this value describes the size of one queue.
     */
    private final int queueSize;

    /**
     * The minimum number of spare (free) workers
     * that the pool should try to keep available.
     */
    private final int minSpareThreads;

    /**
     * Creates a new configuration object and validates all values.
     *
     * @param corePoolSize minimum number of worker threads
     * @param maxPoolSize maximum number of worker threads
     * @param keepAliveTime idle timeout value
     * @param timeUnit unit of time for idle timeout
     * @param queueSize capacity of one worker queue
     * @param minSpareThreads minimum number of free workers to keep ready
     */
    public PoolConfig(int corePoolSize,
                      int maxPoolSize,
                      long keepAliveTime,
                      TimeUnit timeUnit,
                      int queueSize,
                      int minSpareThreads) {

        validate(corePoolSize, maxPoolSize, keepAliveTime, timeUnit, queueSize, minSpareThreads);

        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;
    }

    /**
     * Validates constructor arguments to prevent invalid pool configuration.
     *
     * @param corePoolSize minimum number of worker threads
     * @param maxPoolSize maximum number of worker threads
     * @param keepAliveTime idle timeout value
     * @param timeUnit unit of time for idle timeout
     * @param queueSize capacity of one worker queue
     * @param minSpareThreads minimum number of free workers to keep ready
     */
    private void validate(int corePoolSize,
                          int maxPoolSize,
                          long keepAliveTime,
                          TimeUnit timeUnit,
                          int queueSize,
                          int minSpareThreads) {

        if (corePoolSize < 1) {
            throw new IllegalArgumentException("corePoolSize must be at least 1");
        }

        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("maxPoolSize must be greater than or equal to corePoolSize");
        }

        if (keepAliveTime < 0) {
            throw new IllegalArgumentException("keepAliveTime cannot be negative");
        }

        if (timeUnit == null) {
            throw new IllegalArgumentException("timeUnit cannot be null");
        }

        if (queueSize < 1) {
            throw new IllegalArgumentException("queueSize must be at least 1");
        }

        if (minSpareThreads < 0) {
            throw new IllegalArgumentException("minSpareThreads cannot be negative");
        }

        if (minSpareThreads > maxPoolSize) {
            throw new IllegalArgumentException("minSpareThreads cannot be greater than maxPoolSize");
        }
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public int getMinSpareThreads() {
        return minSpareThreads;
    }
}