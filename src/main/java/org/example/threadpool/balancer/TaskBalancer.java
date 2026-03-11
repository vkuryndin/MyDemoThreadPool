package org.example.threadpool.balancer;

/**
 * This interface defines a strategy for choosing
 * which worker queue should receive the next task.
 *
 * The balancer does not need to know full worker details.
 * It only chooses an index of the target queue.
 */
public interface TaskBalancer {

    /**
     * Selects the index of the queue that should receive the next task.
     *
     * @param queueCount the number of available worker queues
     * @return the index of the selected queue
     */
    int selectQueueIndex(int queueCount);
}
