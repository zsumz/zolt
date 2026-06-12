package com.zolt.toml;

import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.BuildSettings;
import com.zolt.project.ResourceFilteringSettings;
import com.zolt.project.ResourceMissingTokenPolicy;
import com.zolt.project.ResourceTokenSettings;
import com.zolt.project.TestRuntimeSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

final class BuildSectionCodec {
    private static final Set<String> PROJECT_KEYS = Set.of("name", "version", "group", "java", "main");
    private static final Set<String> BUILD_KEYS = Set.of("source", "test", "output", "testOutput", "metadata");
    private static final Set<String> TEST_RUNTIME_KEYS =
            Set.of("jvmArgs", "systemProperties", "environment", "events");
    private static final Set<String> BUILD_METADATA_KEYS = Set.of("buildInfo", "git", "reproducible");
    private static final Set<String> RESOURCES_KEYS = Set.of("main", "test", "filtering", "tokens");
    private static final Set<String> RESOURCE_FILTERING_KEYS = Set.of("enabled", "test", "includes", "missing");
    private static final Set<String> RESOURCE_TOKEN_KEYS = Set.of("value", "env", "project");

    private BuildSectionCodec() {
    }

    static BuildSettings parseBuild(TomlTable table) {
        BuildSettings defaults = BuildSettings.defaults();
        if (table == null) {
            return defaults;
        }

        TomlValidation.validateKeysWithVersionRefHint("build", table, BUILD_KEYS);
        BuildMetadataSettings metadata = parseBuildMetadata(optionalTable(table, "metadata"));
        return new BuildSettings(
                stringOrDefault(table, "build", "source", defaults.source()),
                stringOrDefault(table, "build", "test", defaults.test()),
                stringOrDefault(table, "build", "output", defaults.output()),
                stringOrDefault(table, "build", "testOutput", defaults.testOutput()),
                defaults.testSources(),
                defaults.groovyTestSources(),
                defaults.resourceRoots(),
                defaults.testResourceRoots(),
                metadata);
    }

    static BuildSettings parseTestSources(TomlTable testTable, BuildSettings build) {
        if (testTable == null) {
            return build;
        }
        TomlTable sourcesTable = optionalTable(testTable, "sources");
        if (sourcesTable == null) {
            return build;
        }
        TomlValidation.validateKeysWithVersionRefHint("test.sources", sourcesTable, Set.of("java", "groovy"));
        return new BuildSettings(
                build.source(),
                build.test(),
                build.output(),
                build.testOutput(),
                stringListOrDefault(sourcesTable, "test.sources", "java", build.testSources()),
                stringListOrDefault(sourcesTable, "test.sources", "groovy", build.groovyTestSources()),
                build.resourceRoots(),
                build.testResourceRoots(),
                build.metadata());
    }

    static BuildSettings parseTestRuntime(TomlTable testTable, BuildSettings build) {
        if (testTable == null) {
            return build;
        }
        TomlTable runtimeTable = optionalTable(testTable, "runtime");
        if (runtimeTable == null) {
            return build;
        }
        TomlValidation.validateKeysWithVersionRefHint("test.runtime", runtimeTable, TEST_RUNTIME_KEYS);
        try {
            return build.withTestRuntime(new TestRuntimeSettings(
                    stringListOrDefault(runtimeTable, "test.runtime", "jvmArgs", List.of()),
                    stringMap(optionalTable(runtimeTable, "systemProperties"), "test.runtime.systemProperties"),
                    stringMap(optionalTable(runtimeTable, "environment"), "test.runtime.environment"),
                    stringListOrDefault(runtimeTable, "test.runtime", "events", List.of())));
        } catch (IllegalArgumentException exception) {
            throw new ZoltConfigException(exception.getMessage());
        }
    }

    static BuildSettings parseResourceRoots(TomlTable table, BuildSettings build) {
        if (table == null) {
            return build;
        }
        TomlValidation.validateKeysWithVersionRefHint("resources", table, RESOURCES_KEYS);
        return new BuildSettings(
                build.source(),
                build.test(),
                build.output(),
                build.testOutput(),
                build.testSources(),
                build.groovyTestSources(),
                stringListOrDefault(table, "resources", "main", build.resourceRoots()),
                stringListOrDefault(table, "resources", "test", build.testResourceRoots()),
                parseResourceFiltering(
                        optionalTable(table, "filtering"),
                        optionalTable(table, "tokens")),
                build.metadata());
    }

    static void writeBuild(StringBuilder toml, BuildSettings build) {
        toml.append("[build]\n");
        writeAssignment(toml, "source", build.source());
        writeAssignment(toml, "test", build.test());
        writeAssignment(toml, "output", build.output());
        writeAssignment(toml, "testOutput", build.testOutput());
    }

    static void writeBuildMetadata(StringBuilder toml, BuildMetadataSettings metadata) {
        if (metadata == null || metadata.equals(BuildMetadataSettings.defaults())) {
            return;
        }
        toml.append("\n[build.metadata]\n");
        writeAssignment(toml, "buildInfo", metadata.buildInfo());
        writeAssignment(toml, "git", metadata.git());
        writeAssignment(toml, "reproducible", metadata.reproducible());
    }

    static void writeTestSources(StringBuilder toml, BuildSettings build) {
        if (build.testSources().equals(List.of(build.test())) && build.groovyTestSources().isEmpty()) {
            return;
        }
        toml.append("[test.sources]\n");
        if (!build.testSources().equals(List.of(build.test()))) {
            writeStringArray(toml, "java", build.testSources());
        }
        if (!build.groovyTestSources().isEmpty()) {
            writeStringArray(toml, "groovy", build.groovyTestSources());
        }
        toml.append('\n');
    }

    static void writeTestRuntime(StringBuilder toml, TestRuntimeSettings runtime) {
        if (runtime == null || runtime.defaultsOnly()) {
            return;
        }
        toml.append("[test.runtime]\n");
        if (!runtime.jvmArgs().isEmpty()) {
            writeStringArray(toml, "jvmArgs", runtime.jvmArgs());
        }
        if (!runtime.systemProperties().isEmpty()) {
            writeInlineStringMap(toml, "systemProperties", runtime.systemProperties());
        }
        if (!runtime.environment().isEmpty()) {
            writeInlineStringMap(toml, "environment", runtime.environment());
        }
        if (!runtime.events().isEmpty()) {
            writeStringArray(toml, "events", runtime.events());
        }
        toml.append('\n');
    }

    static void writeResources(StringBuilder toml, BuildSettings build) {
        BuildSettings defaults = BuildSettings.defaults();
        boolean customRoots = !build.resourceRoots().equals(defaults.resourceRoots())
                || !build.testResourceRoots().equals(defaults.testResourceRoots());
        if (customRoots) {
            toml.append("\n[resources]\n");
            writeStringArray(toml, "main", build.resourceRoots());
            writeStringArray(toml, "test", build.testResourceRoots());
        }
        writeResourceFiltering(toml, build.resourceFiltering());
    }

    private static BuildMetadataSettings parseBuildMetadata(TomlTable table) {
        BuildMetadataSettings defaults = BuildMetadataSettings.defaults();
        if (table == null) {
            return defaults;
        }
        TomlValidation.validateKeysWithVersionRefHint("build.metadata", table, BUILD_METADATA_KEYS);
        return new BuildMetadataSettings(
                booleanOrDefault(table, "build.metadata", "buildInfo", defaults.buildInfo()),
                booleanOrDefault(table, "build.metadata", "git", defaults.git()),
                booleanOrDefault(table, "build.metadata", "reproducible", defaults.reproducible()));
    }

    private static ResourceFilteringSettings parseResourceFiltering(
            TomlTable filteringTable,
            TomlTable tokensTable) {
        ResourceFilteringSettings defaults = ResourceFilteringSettings.defaults();
        Map<String, ResourceTokenSettings> tokens = resourceTokens(tokensTable);
        if (filteringTable == null) {
            return tokens.isEmpty()
                    ? defaults
                    : new ResourceFilteringSettings(
                            defaults.enabled(),
                            defaults.testEnabled(),
                            defaults.includes(),
                            defaults.missing(),
                            tokens);
        }
        TomlValidation.validateKeysWithVersionRefHint("resources.filtering", filteringTable, RESOURCE_FILTERING_KEYS);
        String rawMissing = stringOrDefault(
                filteringTable,
                "resources.filtering",
                "missing",
                defaults.missing().configValue());
        ResourceMissingTokenPolicy missing = ResourceMissingTokenPolicy.fromConfigValue(rawMissing)
                .orElseThrow(() -> new ZoltConfigException(
                        "Unsupported resource filtering missing-token policy `"
                                + rawMissing
                                + "` in zolt.toml. Supported values are: "
                                + ResourceMissingTokenPolicy.supportedValues()
                                + "."));
        return new ResourceFilteringSettings(
                booleanOrDefault(filteringTable, "resources.filtering", "enabled", defaults.enabled()),
                booleanOrDefault(filteringTable, "resources.filtering", "test", defaults.testEnabled()),
                stringListOrDefault(filteringTable, "resources.filtering", "includes", defaults.includes()),
                missing,
                tokens);
    }

    private static Map<String, ResourceTokenSettings> resourceTokens(TomlTable table) {
        if (table == null) {
            return Map.of();
        }
        Map<String, ResourceTokenSettings> tokens = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            if (key.isBlank()) {
                throw new ZoltConfigException(
                        "Invalid resource token name in [resources.tokens]. Use a non-empty token name.");
            }
            TomlTable tokenTable = table.getTable(List.of(key));
            if (tokenTable == null) {
                throw new ZoltConfigException(
                        "Invalid value for [resources.tokens]."
                                + key
                                + " in zolt.toml. Use exactly one of { value = \"...\" }, { env = \"...\" }, or { project = \"...\" }.");
            }
            TomlValidation.validateKeysWithVersionRefHint("resources.tokens." + key, tokenTable, RESOURCE_TOKEN_KEYS);
            Optional<String> value = optionalString(tokenTable, "resources.tokens." + key, "value");
            Optional<String> env = optionalString(tokenTable, "resources.tokens." + key, "env");
            Optional<String> project = optionalString(tokenTable, "resources.tokens." + key, "project");
            int sourceCount = (value.isPresent() ? 1 : 0) + (env.isPresent() ? 1 : 0) + (project.isPresent() ? 1 : 0);
            if (sourceCount != 1) {
                throw new ZoltConfigException(
                        "Invalid value for [resources.tokens]."
                                + key
                                + " in zolt.toml. Declare exactly one of value, env, or project.");
            }
            project.ifPresent(projectField -> validateResourceTokenProjectField(key, projectField));
            tokens.put(key, new ResourceTokenSettings(value, env, project));
        }
        return tokens;
    }

    private static void validateResourceTokenProjectField(String tokenName, String projectField) {
        if (!PROJECT_KEYS.contains(projectField)) {
            throw new ZoltConfigException(
                    "Invalid value for [resources.tokens]."
                            + tokenName
                            + ".project in zolt.toml. Supported project fields are: name, version, group, java, main.");
        }
    }

    private static void writeResourceFiltering(StringBuilder toml, ResourceFilteringSettings filtering) {
        if (filtering == null || filtering.equals(ResourceFilteringSettings.defaults())) {
            return;
        }
        toml.append("\n[resources.filtering]\n");
        writeAssignment(toml, "enabled", filtering.enabled());
        if (filtering.testEnabled()) {
            writeAssignment(toml, "test", true);
        }
        if (!filtering.includes().isEmpty()) {
            writeStringArray(toml, "includes", filtering.includes());
        }
        if (filtering.missing() != ResourceMissingTokenPolicy.FAIL) {
            writeAssignment(toml, "missing", filtering.missing().configValue());
        }
        if (!filtering.tokens().isEmpty()) {
            toml.append("\n[resources.tokens]\n");
            for (Map.Entry<String, ResourceTokenSettings> entry : new TreeMap<>(filtering.tokens()).entrySet()) {
                toml.append(quote(entry.getKey())).append(" = ");
                writeResourceToken(toml, entry.getValue());
                toml.append('\n');
            }
        }
    }

    private static void writeResourceToken(StringBuilder toml, ResourceTokenSettings token) {
        token.value().ifPresentOrElse(
                value -> toml.append("{ value = ").append(quote(value)).append(" }"),
                () -> token.env().ifPresentOrElse(
                        env -> toml.append("{ env = ").append(quote(env)).append(" }"),
                        () -> toml.append("{ project = ")
                                .append(quote(token.project().orElseThrow()))
                                .append(" }")));
    }

    private static TomlTable optionalTable(TomlTable table, String key) {
        return table.getTable(List.of(key));
    }

    private static Optional<String> optionalString(TomlTable table, String section, String key) {
        String value = table.getString(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static String stringOrDefault(TomlTable table, String section, String key, String defaultValue) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return defaultValue;
        }
        if (!(rawValue instanceof String value)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use a non-empty string value.");
        }
        if (value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static boolean booleanOrDefault(TomlTable table, String section, String key, boolean defaultValue) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return defaultValue;
        }
        if (!(rawValue instanceof Boolean value)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use true or false.");
        }
        return value;
    }

    private static Map<String, String> stringMap(TomlTable table, String section) {
        if (table == null) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            Object rawValue = table.get(List.of(key));
            if (!(rawValue instanceof String value) || value.isBlank()) {
                throw new ZoltConfigException(
                        "Invalid value for [" + section + "]." + key + " in zolt.toml. Use a non-empty string value.");
            }
            values.put(key, value);
        }
        return values;
    }

    private static List<String> stringListOrDefault(
            TomlTable table,
            String section,
            String key,
            List<String> defaultValue) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return defaultValue;
        }
        if (!(rawValue instanceof TomlArray array)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use an array of strings.");
        }
        List<String> values = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            Object element = array.get(index);
            if (!(element instanceof String value) || value.isBlank()) {
                throw new ZoltConfigException(
                        "Invalid value for [" + section + "]." + key + "[" + index + "] in zolt.toml. Use a non-empty string.");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static void writeAssignment(StringBuilder toml, String key, boolean value) {
        toml.append(key).append(" = ").append(value).append('\n');
    }

    private static void writeAssignment(StringBuilder toml, String key, String value) {
        toml.append(key).append(" = ").append(quote(value)).append('\n');
    }

    private static void writeStringArray(StringBuilder toml, String key, List<String> values) {
        toml.append(key).append(" = [");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                toml.append(", ");
            }
            toml.append(quote(values.get(index)));
        }
        toml.append("]\n");
    }

    private static void writeInlineStringMap(StringBuilder toml, String key, Map<String, String> values) {
        toml.append(key).append(" = { ");
        int index = 0;
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            if (index > 0) {
                toml.append(", ");
            }
            toml.append(quote(entry.getKey())).append(" = ").append(quote(entry.getValue()));
            index++;
        }
        toml.append(" }\n");
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
