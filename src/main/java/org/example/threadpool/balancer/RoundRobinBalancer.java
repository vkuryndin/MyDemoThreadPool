package org.example.threadpool.balancer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This balancer distributes tasks using the Round Robin strategy.
 *
 * <p>It sends tasks to queues one by one in a circular order: 0, 1, 2, 0, 1, 2, ...
 */
public class RoundRobinBalancer implements TaskBalancer {

  /** A thread-safe counter used to rotate through queue indexes. */
  private final AtomicInteger nextIndex = new AtomicInteger(0);

  /**
   * Selects the next queue index in circular order.
   *
   * @param queueCount the number of available worker queues
   * @return the selected queue index
   */
  @Override
  public int selectQueueIndex(int queueCount) {
    if (queueCount <= 0) {
      throw new IllegalArgumentException("queueCount must be greater than 0");
    }

    int currentIndex = nextIndex.getAndIncrement();
    return Math.floorMod(currentIndex, queueCount);
  }
}
