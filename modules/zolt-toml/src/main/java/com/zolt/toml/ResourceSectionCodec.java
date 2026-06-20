package com.zolt.toml;

import com.zolt.project.BuildSettings;
import com.zolt.project.ResourceFilteringSettings;
import com.zolt.project.ResourceMissingTokenPolicy;
import com.zolt.project.ResourceTokenSettings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.tomlj.TomlTable;

final class ResourceSectionCodec {
    private static final Set<String> PROJECT_KEYS = Set.of("name", "version", "group", "java", "main");
    private static final Set<String> RESOURCES_KEYS = Set.of("main", "test", "filtering", "tokens");
    private static final Set<String> RESOURCE_FILTERING_KEYS = Set.of("enabled", "test", "includes", "missing");
    private static final Set<String> RESOURCE_TOKEN_KEYS = Set.of("value", "env", "project");

    private ResourceSectionCodec() {
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
                build.integrationTestOutput(),
                build.integrationTestSources(),
                build.integrationTestResourceRoots(),
                TomlScalars.stringListOrDefault(table, "resources", "main", build.resourceRoots()),
                TomlScalars.stringListOrDefault(table, "resources", "test", build.testResourceRoots()),
                parseResourceFiltering(
                        optionalTable(table, "filtering"),
                        optionalTable(table, "tokens")),
                build.testRuntime(),
                build.metadata(),
                build.generatedMainSources(),
                build.generatedTestSources());
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
        String rawMissing = TomlScalars.stringOrDefault(
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
                TomlScalars.booleanOrDefault(filteringTable, "resources.filtering", "enabled", defaults.enabled()),
                TomlScalars.booleanOrDefault(filteringTable, "resources.filtering", "test", defaults.testEnabled()),
                TomlScalars.stringListOrDefault(
                        filteringTable,
                        "resources.filtering",
                        "includes",
                        defaults.includes()),
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
            Optional<String> value = TomlScalars.optionalString(tokenTable, "resources.tokens." + key, "value");
            Optional<String> env = TomlScalars.optionalString(tokenTable, "resources.tokens." + key, "env");
            Optional<String> project = TomlScalars.optionalString(tokenTable, "resources.tokens." + key, "project");
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
