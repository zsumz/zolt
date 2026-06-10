package org.apache.commons.cli;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Small option parser for the canary fixture.
 */
public final class DefaultParser {
    /**
     * Parses command-line arguments against the declared options.
     *
     * @param options available options
     * @param arguments command-line arguments
     * @return parsed command line
     * @throws ParseException when an option is unknown or malformed
     */
    public CommandLine parse(Options options, String[] arguments) throws ParseException {
        Set<String> present = new LinkedHashSet<String>();
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (int index = 0; index < arguments.length; index++) {
            String token = arguments[index];
            if (!token.startsWith("-")) {
                throw new ParseException("Unexpected argument `" + token + "`.");
            }
            Option option = options.getOption(token);
            if (option == null) {
                throw new ParseException("Unknown option `" + token + "`.");
            }
            present.add(option.opt());
            if (!option.longOpt().isEmpty()) {
                present.add(option.longOpt());
            }
            if (option.hasArg()) {
                if (index + 1 >= arguments.length || arguments[index + 1].startsWith("-")) {
                    throw new ParseException("Missing argument for option `" + token + "`.");
                }
                String value = arguments[++index];
                values.put(option.opt(), value);
                if (!option.longOpt().isEmpty()) {
                    values.put(option.longOpt(), value);
                }
            }
        }
        for (Option option : options.getOptions()) {
            if (option.required() && !present.contains(option.opt())) {
                throw new ParseException("Missing required option `-" + option.opt() + "`.");
            }
        }
        return new CommandLine(present, values);
    }
}
