package org.slf4j;

/**
 * Minimal logger factory facade.
 */
public final class LoggerFactory {
    private static volatile LoggerProvider provider = new NopLoggerProvider();

    private LoggerFactory() {
    }

    /**
     * Installs a logger provider.
     *
     * @param nextProvider provider to install
     */
    public static void setProvider(LoggerProvider nextProvider) {
        if (nextProvider == null) {
            throw new IllegalArgumentException("provider is required");
        }
        provider = nextProvider;
    }

    /**
     * Returns a logger for a class.
     *
     * @param type declaring type
     * @return logger
     */
    public static Logger getLogger(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        return provider.getLogger(type.getName());
    }

    private static final class NopLoggerProvider implements LoggerProvider {
        @Override
        public Logger getLogger(String name) {
            return new NopLogger(name);
        }
    }

    private static final class NopLogger implements Logger {
        private final String name;

        private NopLogger(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void debug(String message) {
        }
    }
}
