package sh.zolt.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlTable;

/**
 * Shared TOML value readers for the user-global config: typed key lookups with the config's uniform
 * "Invalid value for [section].key" diagnostics, plus {@code ~} home expansion and config-relative path
 * resolution. Static-imported by {@link UserGlobalConfigParser} and {@link BuildCacheSectionParser} so a
 * single validation contract backs every section.
 */
final class UserGlobalConfigToml {
    private UserGlobalConfigToml() {
    }

    static String stringOrNull(TomlTable table, String section, String key) {
        Object raw = table.get(List.of(key));
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String value) || value.isBlank()) {
            throw new UserGlobalConfigException(
                    "Invalid value for [" + section + "]." + key + " in user global config. Use a non-empty string.");
        }
        return value.trim();
    }

    static String stringOrDefault(TomlTable table, String section, String key, String defaultValue) {
        Object raw = table.get(List.of(key));
        if (raw == null) {
            return defaultValue;
        }
        if (!(raw instanceof String value) || value.isBlank()) {
            throw new UserGlobalConfigException(
                    "Invalid value for [" + section + "]." + key + " in user global config. Use a non-empty string.");
        }
        return value.trim();
    }

    static String enumOrDefault(
            TomlTable table,
            String section,
            String key,
            String defaultValue,
            Set<String> allowedValues) {
        String value = stringOrDefault(table, section, key, defaultValue);
        if (!allowedValues.contains(value)) {
            throw new UserGlobalConfigException(
                    "Invalid value for [" + section + "]." + key + " in user global config: `" + value + "`. Use one of " + allowedValues + ".");
        }
        return value;
    }

    static int positiveIntOrDefault(TomlTable table, String section, String key, int defaultValue) {
        Object raw = table.get(List.of(key));
        if (raw == null) {
            return defaultValue;
        }
        if (!(raw instanceof Long value) || value < 1 || value > Integer.MAX_VALUE) {
            throw new UserGlobalConfigException(
                    "Invalid value for [" + section + "]." + key + " in user global config. Use a positive integer.");
        }
        return value.intValue();
    }

    static boolean booleanOrDefault(TomlTable table, String section, String key, boolean defaultValue) {
        Object raw = table.get(List.of(key));
        if (raw == null) {
            return defaultValue;
        }
        if (!(raw instanceof Boolean value)) {
            throw new UserGlobalConfigException(
                    "Invalid value for [" + section + "]." + key + " in user global config. Use true or false.");
        }
        return value;
    }

    static void validateKeys(String section, TomlTable table, Set<String> allowedKeys) {
        for (String key : table.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new UserGlobalConfigException(
                        "Unknown key `" + key + "` in [" + section + "] in user global config.");
            }
        }
    }

    static Path resolveConfigRelativePath(String raw, Path configPath) {
        Path path = expandUserHome(Path.of(raw));
        if (!path.isAbsolute()) {
            Path parent = configPath.getParent();
            path = (parent == null ? path : parent.resolve(path));
        }
        return path.toAbsolutePath().normalize();
    }

    static Path expandUserHome(Path path) {
        String value = path.toString();
        String home = System.getProperty("user.home", "");
        if (value.equals("~")) {
            return Path.of(home).toAbsolutePath().normalize();
        }
        if (value.startsWith("~/")) {
            return Path.of(home).resolve(value.substring(2)).toAbsolutePath().normalize();
        }
        return path;
    }
}
