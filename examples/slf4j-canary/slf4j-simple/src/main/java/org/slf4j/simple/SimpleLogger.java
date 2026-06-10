package org.slf4j.simple;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;

/**
 * Simple in-memory logger for canary testing.
 */
public final class SimpleLogger implements Logger {
    private final String name;
    private final List<String> events = new ArrayList<String>();

    SimpleLogger(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void info(String message) {
        events.add("INFO " + name + " - " + message);
    }

    @Override
    public void debug(String message) {
        events.add("DEBUG " + name + " - " + message);
    }

    /**
     * Returns captured log events.
     *
     * @return immutable event list
     */
    public List<String> events() {
        return Collections.unmodifiableList(events);
    }
}
