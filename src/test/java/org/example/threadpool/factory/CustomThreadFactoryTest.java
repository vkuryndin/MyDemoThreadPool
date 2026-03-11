package org.example.threadpool.factory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for CustomThreadFactory.
 *
 * These tests verify:
 * - validation of pool name
 * - correct thread naming
 * - uniqueness of generated thread names
 */
class CustomThreadFactoryTest {

    /**
     * Verifies that the factory does not accept a null pool name.
     */
    @Test
    void shouldThrowWhenPoolNameIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new CustomThreadFactory(null)
        );
    }

    /**
     * Verifies that the factory does not accept a blank pool name.
     */
    @Test
    void shouldThrowWhenPoolNameIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                new CustomThreadFactory("   ")
        );
    }

    /**
     * Verifies that the first created thread gets the expected readable name.
     */
    @Test
    void shouldCreateThreadWithCorrectName() {
        CustomThreadFactory factory = new CustomThreadFactory("MyPool");

        Thread thread = factory.newThread(() -> {
            // No real work is needed for this unit test.
        });

        assertEquals("MyPool-worker-1", thread.getName());
    }

    /**
     * Verifies that thread names are unique and increment correctly.
     */
    @Test
    void shouldCreateThreadsWithUniqueIncrementingNames() {
        CustomThreadFactory factory = new CustomThreadFactory("MyPool");

        Thread thread1 = factory.newThread(() -> {
            // No real work is needed for this unit test.
        });

        Thread thread2 = factory.newThread(() -> {
            // No real work is needed for this unit test.
        });

        Thread thread3 = factory.newThread(() -> {
            // No real work is needed for this unit test.
        });

        assertEquals("MyPool-worker-1", thread1.getName());
        assertEquals("MyPool-worker-2", thread2.getName());
        assertEquals("MyPool-worker-3", thread3.getName());

        assertNotEquals(thread1.getName(), thread2.getName());
        assertNotEquals(thread2.getName(), thread3.getName());
        assertNotEquals(thread1.getName(), thread3.getName());
    }
}