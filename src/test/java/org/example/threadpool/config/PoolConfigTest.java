package org.example.threadpool.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PoolConfig.
 *
 * <p>These tests verify: - correct object creation - validation of invalid constructor arguments
 */
class PoolConfigTest {

  /** Verifies that PoolConfig stores valid constructor arguments correctly. */
  @Test
  void shouldCreateConfigWithValidValues() {
    PoolConfig config = new PoolConfig(2, 4, 5, TimeUnit.SECONDS, 10, 1);

    assertEquals(2, config.getCorePoolSize());
    assertEquals(4, config.getMaxPoolSize());
    assertEquals(5, config.getKeepAliveTime());
    assertEquals(TimeUnit.SECONDS, config.getTimeUnit());
    assertEquals(10, config.getQueueSize());
    assertEquals(1, config.getMinSpareThreads());
  }

  /** Verifies that corePoolSize must be at least 1. */
  @Test
  void shouldThrowWhenCorePoolSizeIsLessThanOne() {
    assertThrows(
        IllegalArgumentException.class, () -> new PoolConfig(0, 4, 5, TimeUnit.SECONDS, 10, 1));
  }

  /** Verifies that maxPoolSize cannot be smaller than corePoolSize. */
  @Test
  void shouldThrowWhenMaxPoolSizeIsLessThanCorePoolSize() {
    assertThrows(
        IllegalArgumentException.class, () -> new PoolConfig(4, 2, 5, TimeUnit.SECONDS, 10, 1));
  }

  /** Verifies that keepAliveTime cannot be negative. */
  @Test
  void shouldThrowWhenKeepAliveTimeIsNegative() {
    assertThrows(
        IllegalArgumentException.class, () -> new PoolConfig(2, 4, -1, TimeUnit.SECONDS, 10, 1));
  }

  /** Verifies that timeUnit cannot be null. */
  @Test
  void shouldThrowWhenTimeUnitIsNull() {
    assertThrows(IllegalArgumentException.class, () -> new PoolConfig(2, 4, 5, null, 10, 1));
  }

  /** Verifies that queueSize must be at least 1. */
  @Test
  void shouldThrowWhenQueueSizeIsLessThanOne() {
    assertThrows(
        IllegalArgumentException.class, () -> new PoolConfig(2, 4, 5, TimeUnit.SECONDS, 0, 1));
  }

  /** Verifies that minSpareThreads cannot be negative. */
  @Test
  void shouldThrowWhenMinSpareThreadsIsNegative() {
    assertThrows(
        IllegalArgumentException.class, () -> new PoolConfig(2, 4, 5, TimeUnit.SECONDS, 10, -1));
  }

  /** Verifies that minSpareThreads cannot be greater than maxPoolSize. */
  @Test
  void shouldThrowWhenMinSpareThreadsIsGreaterThanMaxPoolSize() {
    assertThrows(
        IllegalArgumentException.class, () -> new PoolConfig(2, 4, 5, TimeUnit.SECONDS, 10, 5));
  }
}
