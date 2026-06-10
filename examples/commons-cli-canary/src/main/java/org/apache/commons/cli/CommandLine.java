package org.apache.commons.cli;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parsed command-line state.
 */
public final class CommandLine {
    private final Set<String> presentOptions;
    private final Map<String, String> values;

    CommandLine(Set<String> presentOptions, Map<String, String> values) {
        this.presentOptions = Collections.unmodifiableSet(new LinkedHashSet<String>(presentOptions));
        this.values = Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
    }

    /**
     * Returns true when the option was present in the parsed arguments.
     *
     * @param name short or long option name
     * @return whether the option was present
     */
    public boolean hasOption(String name) {
        return presentOptions.contains(name);
    }

    /**
     * Returns the parsed value for an option.
     *
     * @param name short or long option name
     * @return parsed value, or null when absent
     */
    public String getOptionValue(String name) {
        return values.get(name);
    }
}
