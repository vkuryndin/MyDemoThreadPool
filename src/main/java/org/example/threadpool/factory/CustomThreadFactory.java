package org.example.threadpool.factory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Creates worker threads for the pool. */
@SuppressWarnings("PMD.SystemPrintln")
public final class CustomThreadFactory implements ThreadFactory {

  /** Pool name used as a thread name prefix. */
  private final String poolName;

  /** Counter for worker thread names. */
  private final AtomicInteger threadCounter = new AtomicInteger(1);

  /**
   * @param poolName logical pool name
   */
  public CustomThreadFactory(String poolName) {
    if (poolName == null || poolName.isBlank()) {
      throw new IllegalArgumentException("poolName cannot be null or blank");
    }

    this.poolName = poolName;
  }

  /**
   * Creates the next worker thread.
   *
   * @param runnable runnable to execute in the new thread
   * @return new worker thread
   */
  @Override
  public Thread newThread(Runnable runnable) {
    String threadName = poolName + "-worker-" + threadCounter.getAndIncrement();

    System.out.println("[ThreadFactory] Creating new thread: " + threadName);

    return new Thread(runnable, threadName);
  }
}
