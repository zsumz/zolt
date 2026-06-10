package com.zaxxer.hikari;

/**
 * Signals that the pool could not provide a connection.
 */
public final class PoolInitializationException extends RuntimeException {
    /**
     * Creates a pool initialization exception.
     *
     * @param message user-facing failure message
     */
    public PoolInitializationException(String message) {
        super(message);
    }
}
