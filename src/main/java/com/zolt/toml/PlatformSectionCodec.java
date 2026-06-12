package com.zolt.toml;

import com.zolt.project.DependencyMetadata;
import com.zolt.project.VersionAliasRules;
import com.zolt.project.VersionPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.tomlj.TomlTable;

final class PlatformSectionCodec {
    private static final String SECTION = "platforms";

    private PlatformSectionCodec() {
    }

    static Map<String, String> parse(
            TomlTable table,
            Map<String, String> versionAliases,
            Map<String, DependencyMetadata> dependencyMetadata) {
        if (table == null) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            Object rawValue = table.get(List.of(key));
            if (rawValue instanceof String value) {
                if (value.isBlank()) {
                    throw invalidPlatformValue(key);
                }
                validateVersion(SECTION + "." + key, value);
                values.put(key, value);
                continue;
            }
            if (rawValue instanceof TomlTable valueTable) {
                TomlValidation.validateKeys(SECTION + "." + key, valueTable, Set.of("versionRef"));
                String version = requiredVersionRef(valueTable, SECTION + "." + key, versionAliases);
                values.put(key, version);
                if (valueTable.get(List.of("versionRef")) instanceof String alias) {
                    dependencyMetadata.put(
                            DependencyMetadata.key(SECTION, key),
                            new DependencyMetadata(
                                    SECTION,
                                    key,
                                    version,
                                    alias,
                                    false,
                                    null,
                                    false,
                                    false,
                                    List.of()));
                }
                continue;
            }
            throw invalidPlatformValue(key);
        }
        return values;
    }

    static void write(
            StringBuilder toml,
            Map<String, String> platforms,
            Map<String, DependencyMetadata> dependencyMetadata) {
        toml.append("[platforms]\n");
        for (Map.Entry<String, String> entry : new TreeMap<>(platforms).entrySet()) {
            toml.append(quote(entry.getKey())).append(" = ");
            DependencyMetadata metadata =
                    dependencyMetadata.get(DependencyMetadata.key(SECTION, entry.getKey()));
            if (metadata != null && metadata.versionRef() != null) {
                toml.append("{ versionRef = ").append(quote(metadata.versionRef())).append(" }");
            } else {
                toml.append(quote(entry.getValue()));
            }
            toml.append('\n');
        }
        toml.append('\n');
    }

    private static ZoltConfigException invalidPlatformValue(String key) {
        return new ZoltConfigException(
                "Invalid value for [platforms]." + key + " in zolt.toml. Use a non-empty version string or { versionRef = \"alias\" }.");
    }

    private static void validateVersion(String subject, String version) {
        VersionPolicy.violation(VersionPolicy.Context.PLATFORM, version).ifPresent(violation -> {
            throw new ZoltConfigException(
                    "Invalid "
                            + VersionPolicy.Context.PLATFORM.description()
                            + " `"
                            + version
                            + "` for ["
                            + subject
                            + "] in zolt.toml. "
                            + violation.guidance());
        });
    }

    private static String requiredVersionRef(
            TomlTable table,
            String section,
            Map<String, String> versionAliases) {
        Object rawValue = table.get(List.of("versionRef"));
        if (!(rawValue instanceof String alias) || alias.isBlank()) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "].versionRef in zolt.toml. Use a non-empty alias from [versions].");
        }
        validateAliasName(alias);
        String version = versionAliases.get(alias);
        if (version == null) {
            throw new ZoltConfigException(
                    "Unknown versionRef `"
                            + alias
                            + "` in ["
                            + section
                            + "]. Add [versions]."
                            + alias
                            + " or use an explicit version.");
        }
        return version;
    }

    private static void validateAliasName(String alias) {
        if (!VersionAliasRules.isValidName(alias)) {
            throw new ZoltConfigException(
                    "Invalid [versions] alias `"
                            + alias
                            + "`. Alias names may contain only letters, digits, dot, underscore, and hyphen.");
        }
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
