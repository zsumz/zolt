package com.zaxxer.hikari;

import org.slf4j.Logger;

/**
 * Tiny data source facade for canary testing.
 */
public final class HikariDataSource implements AutoCloseable {
    private final HikariConfig config;
    private final PoolBag poolBag;
    private boolean closed;

    /**
     * Creates a data source from the provided configuration.
     *
     * @param config source configuration
     */
    public HikariDataSource(HikariConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        this.config = config.copy();
        this.poolBag = new PoolBag(this.config.getMaximumPoolSize());
    }

    /**
     * Borrows a connection handle from the pool.
     *
     * @return borrowed handle
     */
    public PooledConnection getConnection() {
        if (closed) {
            throw new IllegalStateException("data source is closed");
        }
        return poolBag.borrow(config.getConnectionTimeoutMillis());
    }

    /**
     * Returns the number of borrowed handles.
     *
     * @return active handle count
     */
    public int getActiveConnections() {
        return poolBag.activeCount();
    }

    /**
     * Returns the configured pool name.
     *
     * @return pool name
     */
    public String getPoolName() {
        return config.getPoolName();
    }

    /**
     * Returns the SLF4J API type used by Hikari-style consumers.
     *
     * @return logger API type
     */
    public Class<Logger> getLoggerApiType() {
        return Logger.class;
    }

    /**
     * Closes the pool.
     */
    @Override
    public void close() {
        closed = true;
        poolBag.close();
    }
}
