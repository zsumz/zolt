package com.zolt.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public final class UserGlobalConfigParser {
    private static final Set<String> TOP_LEVEL_KEYS = Set.of("version", "cache", "repository", "repositoryOverlays", "ui");
    private static final Set<String> REJECTED_TOP_LEVEL_KEYS = Set.of(
            "repositories",
            "repositoryCredentials",
            "dependencies",
            "devDependencies",
            "runtimeDependencies",
            "testDependencies",
            "platforms",
            "versions",
            "dependencyPolicy",
            "package",
            "compiler",
            "native",
            "workspace",
            "members");
    private static final Set<String> CACHE_KEYS = Set.of("root");
    private static final Set<String> REPOSITORY_KEYS = Set.of("downloadConcurrency", "executionLane");
    private static final Set<String> OVERLAY_KEYS = Set.of("kind", "enabled");
    private static final Set<String> UI_KEYS = Set.of("color", "progress");
    private static final Set<String> EXECUTION_LANES = Set.of("platform", "serial");
    private static final Set<String> UI_MODES = Set.of("auto", "always", "never");

    public UserGlobalConfig read(Path configPath) {
        Path normalizedPath = configPath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedPath)) {
            return UserGlobalConfig.defaults(normalizedPath);
        }
        try {
            return parse(Files.readString(normalizedPath), normalizedPath);
        } catch (IOException exception) {
            throw new UserGlobalConfigException(
                    "Could not read user global config at " + normalizedPath + ". Check that the file is readable.",
                    exception);
        }
    }

    UserGlobalConfig parse(String content, Path configPath) {
        Path normalizedPath = configPath.toAbsolutePath().normalize();
        TomlParseResult result = Toml.parse(content);
        if (result.hasErrors()) {
            throw new UserGlobalConfigException(parseErrorMessage(result));
        }
        validateTopLevelKeys(result);
        int version = version(result);
        if (version != 1) {
            throw new UserGlobalConfigException(
                    "Unsupported user global config version " + version + " at " + normalizedPath + ". Use version = 1 with this Zolt version.");
        }

        UserGlobalConfig defaults = UserGlobalConfig.defaults(normalizedPath);
        Path cacheRoot = cacheRoot(optionalTable(result, "cache"), normalizedPath, defaults.cacheRoot());
        RepositoryExecutionConfig repository = repository(optionalTable(result, "repository"), defaults.repository());
        Map<String, RepositoryOverlayConfig> overlays = repositoryOverlays(optionalTable(result, "repositoryOverlays"), defaults.repositoryOverlays());
        UiConfig ui = ui(optionalTable(result, "ui"), defaults.ui());
        UserGlobalConfigSources sources = sources(result, defaults.sources());
        return new UserGlobalConfig(version, normalizedPath, cacheRoot, repository, overlays, ui, sources);
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

    private static Path cacheRoot(TomlTable table, Path configPath, Path defaultValue) {
        if (table == null) {
            return defaultValue;
        }
        validateKeys("cache", table, CACHE_KEYS);
        String raw = stringOrDefault(table, "cache", "root", defaultValue.toString());
        Path path = expandUserHome(Path.of(raw));
        if (!path.isAbsolute()) {
            Path parent = configPath.getParent();
            path = (parent == null ? path : parent.resolve(path));
        }
        return path.toAbsolutePath().normalize();
    }

    private static RepositoryExecutionConfig repository(TomlTable table, RepositoryExecutionConfig defaultValue) {
        if (table == null) {
            return defaultValue;
        }
        validateKeys("repository", table, REPOSITORY_KEYS);
        int downloadConcurrency = positiveIntOrDefault(
                table,
                "repository",
                "downloadConcurrency",
                defaultValue.downloadConcurrency());
        String executionLane = enumOrDefault(
                table,
                "repository",
                "executionLane",
                defaultValue.executionLane(),
                EXECUTION_LANES);
        return new RepositoryExecutionConfig(downloadConcurrency, executionLane);
    }

    private static Map<String, RepositoryOverlayConfig> repositoryOverlays(
            TomlTable table,
            Map<String, RepositoryOverlayConfig> defaults) {
        if (table == null) {
            return defaults;
        }
        Map<String, RepositoryOverlayConfig> overlays = new LinkedHashMap<>(defaults);
        for (String id : table.keySet()) {
            TomlTable overlayTable = table.getTable(List.of(id));
            if (overlayTable == null) {
                throw new UserGlobalConfigException(
                        "Invalid value for [repositoryOverlays]." + id + " in user global config. Use a table with kind and enabled keys.");
            }
            validateKeys("repositoryOverlays." + id, overlayTable, OVERLAY_KEYS);
            String kind = enumOrDefault(
                    overlayTable,
                    "repositoryOverlays." + id,
                    "kind",
                    "maven-local",
                    Set.of("maven-local"));
            boolean enabled = booleanOrDefault(overlayTable, "repositoryOverlays." + id, "enabled", false);
            overlays.put(id, new RepositoryOverlayConfig(id, kind, enabled));
        }
        return overlays;
    }

    private static UiConfig ui(TomlTable table, UiConfig defaultValue) {
        if (table == null) {
            return defaultValue;
        }
        validateKeys("ui", table, UI_KEYS);
        return new UiConfig(
                enumOrDefault(table, "ui", "color", defaultValue.color(), UI_MODES),
                enumOrDefault(table, "ui", "progress", defaultValue.progress(), UI_MODES));
    }

    private static UserGlobalConfigSources sources(TomlParseResult result, UserGlobalConfigSources defaults) {
        TomlTable cacheTable = optionalTable(result, "cache");
        TomlTable repositoryTable = optionalTable(result, "repository");
        TomlTable overlaysTable = optionalTable(result, "repositoryOverlays");
        TomlTable uiTable = optionalTable(result, "ui");
        return new UserGlobalConfigSources(
                UserGlobalConfigSources.USER_GLOBAL_CONFIG,
                source(cacheTable, "root"),
                source(repositoryTable, "downloadConcurrency"),
                source(repositoryTable, "executionLane"),
                overlaySources(overlaysTable, defaults.repositoryOverlays()),
                source(uiTable, "color"),
                source(uiTable, "progress"));
    }

    private static Map<String, RepositoryOverlayConfigSource> overlaySources(
            TomlTable table,
            Map<String, RepositoryOverlayConfigSource> defaults) {
        if (table == null) {
            return defaults;
        }
        Map<String, RepositoryOverlayConfigSource> sources = new LinkedHashMap<>(defaults);
        for (String id : table.keySet()) {
            TomlTable overlayTable = table.getTable(List.of(id));
            if (overlayTable != null) {
                sources.put(id, new RepositoryOverlayConfigSource(
                        source(overlayTable, "kind"),
                        source(overlayTable, "enabled")));
            }
        }
        return sources;
    }

    private static String source(TomlTable table, String key) {
        return table != null && table.get(List.of(key)) != null
                ? UserGlobalConfigSources.USER_GLOBAL_CONFIG
                : UserGlobalConfigSources.BUILT_IN_DEFAULT;
    }

    private static int version(TomlParseResult result) {
        Object raw = result.get(List.of("version"));
        if (raw == null) {
            throw new UserGlobalConfigException("Missing required version in user global config. Add `version = 1`.");
        }
        if (!(raw instanceof Long value)) {
            throw new UserGlobalConfigException("Invalid version in user global config. Use integer `version = 1`.");
        }
        return Math.toIntExact(value);
    }

    private static String stringOrDefault(TomlTable table, String section, String key, String defaultValue) {
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

    private static String enumOrDefault(
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

    private static int positiveIntOrDefault(TomlTable table, String section, String key, int defaultValue) {
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

    private static boolean booleanOrDefault(TomlTable table, String section, String key, boolean defaultValue) {
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

    private static void validateTopLevelKeys(TomlParseResult result) {
        for (String key : result.keySet()) {
            if (REJECTED_TOP_LEVEL_KEYS.contains(key)) {
                throw new UserGlobalConfigException(
                        "Global config section [" + key + "] is not supported. Move project semantics to committed zolt.toml so lockfiles are reproducible in CI.");
            }
            if (!TOP_LEVEL_KEYS.contains(key)) {
                throw new UserGlobalConfigException("Unknown top-level key `" + key + "` in user global config.");
            }
        }
    }

    private static void validateKeys(String section, TomlTable table, Set<String> allowedKeys) {
        for (String key : table.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new UserGlobalConfigException(
                        "Unknown key `" + key + "` in [" + section + "] in user global config.");
            }
        }
    }

    private static TomlTable optionalTable(TomlParseResult result, String section) {
        return result.getTable(section);
    }

    private static String parseErrorMessage(TomlParseResult result) {
        TomlParseError error = result.errors().getFirst();
        return "Invalid user global config: " + error;
    }
}
