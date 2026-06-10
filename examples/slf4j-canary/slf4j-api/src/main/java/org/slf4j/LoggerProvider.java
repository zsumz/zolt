package org.slf4j;

/**
 * Supplies loggers to the API facade.
 */
public interface LoggerProvider {
    /**
     * Creates or returns a logger.
     *
     * @param name logger name
     * @return logger
     */
    Logger getLogger(String name);
}
