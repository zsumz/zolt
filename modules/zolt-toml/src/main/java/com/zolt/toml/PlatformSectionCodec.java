package com.zolt.toml;

import com.zolt.toml.support.TomlVersions;
import com.zolt.toml.support.TomlValidation;
import com.zolt.project.DependencyMetadata;
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
                TomlVersions.validateVersion(VersionPolicy.Context.PLATFORM, SECTION + "." + key, value);
                values.put(key, value);
                continue;
            }
            if (rawValue instanceof TomlTable valueTable) {
                TomlValidation.validateKeys(SECTION + "." + key, valueTable, Set.of("versionRef"));
                String version = TomlVersions.requiredVersionRef(valueTable, SECTION + "." + key, versionAliases);
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
