package org.example.threadpool.config;

import java.util.concurrent.TimeUnit;

/**
 * This class stores all configuration parameters of the custom thread pool.
 *
 * <p>We keep pool settings in a separate object to make the code cleaner and to avoid passing many
 * constructor arguments one by one.
 */
public final class PoolConfig {

  /** The minimum number of worker threads that should exist in the pool. */
  private final int corePoolSize;

  /** The maximum number of worker threads that can exist in the pool. */
  private final int maxPoolSize;

  /** The amount of time that an idle worker can stay alive before it is allowed to stop. */
  private final long keepAliveTime;

  /** The time unit for keepAliveTime. */
  private final TimeUnit timeUnit;

  /**
   * The capacity of a single worker queue.
   *
   * <p>In our design, every worker has its own queue, so this value describes the size of one
   * queue.
   */
  private final int queueSize;

  /** The minimum number of spare (free) workers that the pool should try to keep available. */
  private final int minSpareThreads;

  private static final int MIN_POOL_SIZE = 1;

  private static final int MIN_QUEUE_SIZE = 1;

  private static final int MIN_SPARE_THREADS = 0;

  private static final long MIN_KEEP_ALIVE_TIME = 0L;

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
  public PoolConfig(
      int corePoolSize,
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
  private void validate(
      int corePoolSize,
      int maxPoolSize,
      long keepAliveTime,
      TimeUnit timeUnit,
      int queueSize,
      int minSpareThreads) {

    if (corePoolSize < MIN_POOL_SIZE) {
      throw new IllegalArgumentException("corePoolSize must be at least 1");
    }

    if (maxPoolSize < corePoolSize) {
      throw new IllegalArgumentException(
          "maxPoolSize must be greater than or equal to corePoolSize");
    }

    if (keepAliveTime < MIN_KEEP_ALIVE_TIME) {
      throw new IllegalArgumentException("keepAliveTime cannot be negative");
    }

    if (timeUnit == null) {
      throw new IllegalArgumentException("timeUnit cannot be null");
    }

    if (queueSize < MIN_QUEUE_SIZE) {
      throw new IllegalArgumentException("queueSize must be at least 1");
    }

    if (minSpareThreads < MIN_SPARE_THREADS) {
      throw new IllegalArgumentException("minSpareThreads cannot be negative");
    }

    if (minSpareThreads > maxPoolSize) {
      throw new IllegalArgumentException("minSpareThreads cannot be greater than maxPoolSize");
    }
  }

  /**
   * Returns the configured core pool size.
   *
   * @return core pool size
   */
  public int getCorePoolSize() {
    return corePoolSize;
  }

  /**
   * Returns the configured maximum pool size.
   *
   * @return maximum pool size
   */
  public int getMaxPoolSize() {
    return maxPoolSize;
  }

  /**
   * Returns the configured keep-alive time for extra workers.
   *
   * @return keep-alive time
   */
  public long getKeepAliveTime() {
    return keepAliveTime;
  }

  /**
   * Returns the time unit used for keep-alive time.
   *
   * @return keep-alive time unit
   */
  public TimeUnit getTimeUnit() {
    return timeUnit;
  }

  /**
   * Returns the capacity of one worker queue.
   *
   * @return queue capacity
   */
  public int getQueueSize() {
    return queueSize;
  }

  /**
   * Returns the minimum number of spare idle workers.
   *
   * @return minimum spare worker count
   */
  public int getMinSpareThreads() {
    return minSpareThreads;
  }
}
