package org.example.threadpool.rejection;

import org.junit.jupiter.api.Test;

import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for RejectPolicy.
 *
 * These tests verify that the policy rejects tasks
 * by throwing RejectedExecutionException.
 */
class RejectPolicyTest {

    /**
     * Verifies that reject() throws RejectedExecutionException
     * for a normal Runnable task.
     */
    @Test
    void shouldThrowRejectedExecutionExceptionWhenTaskIsRejected() {
        RejectPolicy policy = new RejectPolicy();

        Runnable task = () -> {
            // Simple demo task for testing rejection behavior.
        };

        assertThrows(RejectedExecutionException.class, () ->
                policy.reject(task)
        );
    }

    /**
     * Verifies that reject() also throws RejectedExecutionException
     * even if the task has a custom toString() representation.
     */
    @Test
    void shouldThrowRejectedExecutionExceptionForTaskWithCustomDescription() {
        RejectPolicy policy = new RejectPolicy();

        Runnable task = new Runnable() {
            @Override
            public void run() {
                // No real work is needed for this unit test.
            }

            @Override
            public String toString() {
                return "TestRunnable";
            }
        };

        assertThrows(RejectedExecutionException.class, () ->
                policy.reject(task)
        );
    }
}