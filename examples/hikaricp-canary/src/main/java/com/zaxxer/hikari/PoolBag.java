package com.zaxxer.hikari;

import java.util.ArrayDeque;
import java.util.Queue;

final class PoolBag {
    private final Queue<PooledConnection> idle = new ArrayDeque<PooledConnection>();
    private final int maximumPoolSize;
    private int created;
    private int active;
    private boolean closed;

    PoolBag(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    synchronized PooledConnection borrow(long timeoutMillis) {
        if (closed) {
            throw new IllegalStateException("pool is closed");
        }
        PooledConnection connection = idle.poll();
        if (connection == null) {
            if (created >= maximumPoolSize) {
                throw new PoolInitializationException(
                        "Timed out after " + timeoutMillis + "ms waiting for a pooled connection.");
            }
            connection = new PooledConnection(this, ++created);
        }
        active++;
        return connection.activate();
    }

    synchronized void release(PooledConnection connection) {
        if (closed || !connection.markReleased()) {
            return;
        }
        active--;
        idle.add(connection);
    }

    synchronized int activeCount() {
        return active;
    }

    synchronized void close() {
        closed = true;
        idle.clear();
        active = 0;
    }
}
