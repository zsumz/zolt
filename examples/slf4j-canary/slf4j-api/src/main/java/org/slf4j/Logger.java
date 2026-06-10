package org.slf4j;

/**
 * Minimal logging API for the canary fixture.
 */
public interface Logger {
    /**
     * Returns the logger name.
     *
     * @return logger name
     */
    String getName();

    /**
     * Logs an informational message.
     *
     * @param message message to log
     */
    void info(String message);

    /**
     * Logs a debug message.
     *
     * @param message message to log
     */
    void debug(String message);
}
