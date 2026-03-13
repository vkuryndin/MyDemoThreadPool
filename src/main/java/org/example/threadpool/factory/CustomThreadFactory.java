package org.example.threadpool.factory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class is responsible for creating worker threads for the custom pool.
 *
 * <p>It gives each thread a unique name and logs the thread creation event.
 */
@SuppressWarnings("PMD.SystemPrintln")
public final class CustomThreadFactory implements ThreadFactory {

  /** The logical name of the pool. It will be used as a prefix for worker thread names. */
  private final String poolName;

  /** A thread-safe counter used to generate unique worker numbers. */
  private final AtomicInteger threadCounter = new AtomicInteger(1);

  /**
   * Creates a new thread factory for the given pool name.
   *
   * @param poolName the name of the pool
   */
  public CustomThreadFactory(String poolName) {
    if (poolName == null || poolName.isBlank()) {
      throw new IllegalArgumentException("poolName cannot be null or blank");
    }

    this.poolName = poolName;
  }

  /**
   * Creates a new thread with a unique name.
   *
   * <p>Example: MyPool-worker-1 MyPool-worker-2
   *
   * @param runnable the task that the new thread will run
   * @return a new Thread object
   */
  @Override
  public Thread newThread(Runnable runnable) {
    String threadName = poolName + "-worker-" + threadCounter.getAndIncrement();

    System.out.println("[ThreadFactory] Creating new thread: " + threadName);

    return new Thread(runnable, threadName);
  }
}
