package org.example.threadpool.balancer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for RoundRobinBalancer.
 *
 * These tests verify:
 * - correct circular index selection
 * - validation of invalid queue count
 */
class RoundRobinBalancerTest {

    /**
     * Verifies that the balancer returns indexes in circular order
     * when there are three queues.
     */
    @Test
    void shouldReturnIndexesInRoundRobinOrderForThreeQueues() {
        RoundRobinBalancer balancer = new RoundRobinBalancer();

        assertEquals(0, balancer.selectQueueIndex(3));
        assertEquals(1, balancer.selectQueueIndex(3));
        assertEquals(2, balancer.selectQueueIndex(3));
        assertEquals(0, balancer.selectQueueIndex(3));
        assertEquals(1, balancer.selectQueueIndex(3));
        assertEquals(2, balancer.selectQueueIndex(3));
    }

    /**
     * Verifies that the balancer always returns 0
     * when there is only one queue.
     */
    @Test
    void shouldAlwaysReturnZeroForOneQueue() {
        RoundRobinBalancer balancer = new RoundRobinBalancer();

        assertEquals(0, balancer.selectQueueIndex(1));
        assertEquals(0, balancer.selectQueueIndex(1));
        assertEquals(0, balancer.selectQueueIndex(1));
        assertEquals(0, balancer.selectQueueIndex(1));
    }

    /**
     * Verifies that queueCount must be greater than 0.
     */
    @Test
    void shouldThrowWhenQueueCountIsZero() {
        RoundRobinBalancer balancer = new RoundRobinBalancer();

        assertThrows(IllegalArgumentException.class, () ->
                balancer.selectQueueIndex(0)
        );
    }

    /**
     * Verifies that queueCount must be greater than 0.
     */
    @Test
    void shouldThrowWhenQueueCountIsNegative() {
        RoundRobinBalancer balancer = new RoundRobinBalancer();

        assertThrows(IllegalArgumentException.class, () ->
                balancer.selectQueueIndex(-5)
        );
    }
}