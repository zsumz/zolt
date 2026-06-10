package org.apache.commons.cli;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ordered option declarations.
 */
public final class Options {
    private final Map<String, Option> shortOptions = new LinkedHashMap<String, Option>();
    private final Map<String, Option> longOptions = new LinkedHashMap<String, Option>();

    /**
     * Adds an option declaration.
     *
     * @param option option to add
     * @return this option set
     */
    public Options addOption(Option option) {
        if (option == null) {
            throw new IllegalArgumentException("Option is required.");
        }
        shortOptions.put(option.opt(), option);
        if (!option.longOpt().isEmpty()) {
            longOptions.put(option.longOpt(), option);
        }
        return this;
    }

    /**
     * Returns the option matching a command-line token.
     *
     * @param token option token such as {@code -h} or {@code --help}
     * @return matching option, or null
     */
    public Option getOption(String token) {
        if (token.startsWith("--")) {
            return longOptions.get(token.substring(2));
        }
        if (token.startsWith("-")) {
            return shortOptions.get(token.substring(1));
        }
        return null;
    }

    /**
     * Returns options in declaration order.
     *
     * @return declared options
     */
    public Collection<Option> getOptions() {
        return Collections.unmodifiableCollection(shortOptions.values());
    }
}
