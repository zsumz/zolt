package com.zaxxer.hikari;

/**
 * Borrowed connection handle for the canary fixture.
 */
public final class PooledConnection implements AutoCloseable {
    private final PoolBag pool;
    private final int id;
    private boolean active;

    PooledConnection(PoolBag pool, int id) {
        this.pool = pool;
        this.id = id;
    }

    PooledConnection activate() {
        active = true;
        return this;
    }

    boolean markReleased() {
        if (!active) {
            return false;
        }
        active = false;
        return true;
    }

    /**
     * Returns a stable handle id.
     *
     * @return connection handle id
     */
    public int getId() {
        return id;
    }

    /**
     * Returns whether this handle is active.
     *
     * @return active state
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Returns this handle to the pool.
     */
    @Override
    public void close() {
        pool.release(this);
    }
}
