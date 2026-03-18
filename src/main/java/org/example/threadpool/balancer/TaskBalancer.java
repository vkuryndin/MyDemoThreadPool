package org.example.threadpool.balancer;

/** Strategy interface for selecting a worker queue for the next task. */
@FunctionalInterface
public interface TaskBalancer {

  /**
   * Returns the index of the queue that should receive the next task.
   *
   * @param queueCount number of available worker queues
   * @return selected queue index
   */
  int selectQueueIndex(int queueCount);
}
