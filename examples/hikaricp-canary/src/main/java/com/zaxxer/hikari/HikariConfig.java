package com.zaxxer.hikari;

/**
 * Minimal pool configuration for the canary fixture.
 */
public final class HikariConfig {
    private String poolName = "HikariPool";
    private int maximumPoolSize = 10;
    private long connectionTimeoutMillis = 30000L;

    /**
     * Returns the configured pool name.
     *
     * @return pool name
     */
    public String getPoolName() {
        return poolName;
    }

    /**
     * Sets the pool name.
     *
     * @param poolName non-empty pool name
     */
    public void setPoolName(String poolName) {
        if (poolName == null || poolName.trim().isEmpty()) {
            throw new IllegalArgumentException("poolName must not be blank");
        }
        this.poolName = poolName;
    }

    /**
     * Returns the maximum pool size.
     *
     * @return maximum pool size
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Sets the maximum pool size.
     *
     * @param maximumPoolSize maximum pool size, at least one
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize < 1) {
            throw new IllegalArgumentException("maximumPoolSize must be at least 1");
        }
        this.maximumPoolSize = maximumPoolSize;
    }

    /**
     * Returns the connection timeout.
     *
     * @return connection timeout in milliseconds
     */
    public long getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    /**
     * Sets the connection timeout.
     *
     * @param connectionTimeoutMillis timeout in milliseconds, at least 250
     */
    public void setConnectionTimeoutMillis(long connectionTimeoutMillis) {
        if (connectionTimeoutMillis < 250L) {
            throw new IllegalArgumentException("connectionTimeoutMillis must be at least 250");
        }
        this.connectionTimeoutMillis = connectionTimeoutMillis;
    }

    HikariConfig copy() {
        HikariConfig copy = new HikariConfig();
        copy.poolName = poolName;
        copy.maximumPoolSize = maximumPoolSize;
        copy.connectionTimeoutMillis = connectionTimeoutMillis;
        return copy;
    }
}
