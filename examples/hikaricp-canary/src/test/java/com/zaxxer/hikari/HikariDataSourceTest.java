package com.zaxxer.hikari;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class HikariDataSourceTest {
    @Test
    public void borrowsAndReturnsConnectionHandles() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("CanaryPool");
        config.setMaximumPoolSize(1);
        config.setConnectionTimeoutMillis(Long.getLong("hikari.canary.leakDetectionMs", 250L));

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            PooledConnection first = dataSource.getConnection();
            assertTrue(first.isActive());
            assertEquals(1, dataSource.getActiveConnections());
            first.close();

            PooledConnection second = dataSource.getConnection();
            assertSame(first, second);
            assertEquals("CanaryPool", dataSource.getPoolName());
            assertEquals("org.slf4j.Logger", dataSource.getLoggerApiType().getName());
            second.close();
        }
    }

    @Test
    public void rejectsInvalidPoolSize() {
        HikariConfig config = new HikariConfig();

        try {
            config.setMaximumPoolSize(0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertEquals("maximumPoolSize must be at least 1", exception.getMessage());
        }
    }

    @Test
    public void failsWhenPoolIsExhausted() {
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            PooledConnection first = dataSource.getConnection();
            try {
                dataSource.getConnection();
                fail("expected PoolInitializationException");
            } catch (PoolInitializationException exception) {
                assertTrue(exception.getMessage().contains("Timed out after"));
            } finally {
                first.close();
            }
            assertFalse(first.isActive());
        }
    }
}
