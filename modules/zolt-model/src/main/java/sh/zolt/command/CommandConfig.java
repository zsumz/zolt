package sh.zolt.command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CommandConfig(
        Map<String, CommandAlias> aliases,
        Map<String, CommandTask> tasks) {
    public CommandConfig {
        aliases = orderedMap(aliases);
        tasks = orderedMap(tasks);
    }

    public static CommandConfig empty() {
        return new CommandConfig(Map.of(), Map.of());
    }

    private static <T> Map<String, T> orderedMap(Map<String, T> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
