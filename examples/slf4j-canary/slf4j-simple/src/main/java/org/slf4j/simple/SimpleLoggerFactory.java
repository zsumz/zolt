package org.slf4j.simple;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerProvider;

/**
 * Simple logger provider for canary testing.
 */
public final class SimpleLoggerFactory implements LoggerProvider {
    private final Map<String, SimpleLogger> loggers = new LinkedHashMap<String, SimpleLogger>();

    @Override
    public synchronized Logger getLogger(String name) {
        SimpleLogger logger = loggers.get(name);
        if (logger == null) {
            logger = new SimpleLogger(name);
            loggers.put(name, logger);
        }
        return logger;
    }

    /**
     * Returns a typed simple logger.
     *
     * @param name logger name
     * @return simple logger
     */
    public synchronized SimpleLogger getSimpleLogger(String name) {
        return (SimpleLogger) getLogger(name);
    }
}
