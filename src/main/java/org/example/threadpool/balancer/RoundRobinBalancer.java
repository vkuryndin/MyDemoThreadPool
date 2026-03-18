package org.example.threadpool.balancer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Task balancer that distributes tasks in round-robin order.
 *
 * <p>Queue indexes are selected in a circular sequence such as {@code 0, 1, 2, 0, 1, 2}.
 */
public class RoundRobinBalancer implements TaskBalancer {

  /** Counter used to rotate through queue indexes. */
  private final AtomicInteger nextIndex = new AtomicInteger(0);

  /**
   * Returns the next queue index in round-robin order.
   *
   * @param queueCount number of available worker queues
   * @return selected queue index
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
