package com.zolt.toml;

import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyConstraintKind;
import com.zolt.project.DependencyPolicyExclusion;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.project.VersionAliasRules;
import com.zolt.project.VersionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

final class DependencyPolicySectionCodec {
    private static final Set<String> DEPENDENCY_POLICY_KEYS = Set.of("exclude");
    private static final Set<String> DEPENDENCY_POLICY_EXCLUSION_KEYS = Set.of("group", "artifact", "reason");
    private static final Set<String> DEPENDENCY_CONSTRAINT_KEYS = Set.of("version", "versionRef", "kind", "reason");

    private DependencyPolicySectionCodec() {
    }

    static DependencyPolicySettings parse(
            TomlTable policyTable,
            TomlTable constraintsTable,
            Map<String, String> versionAliases) {
        List<DependencyPolicyExclusion> exclusions = dependencyPolicyExclusions(policyTable);
        Map<String, DependencyConstraint> constraints = dependencyConstraints(constraintsTable, versionAliases);
        if (exclusions.isEmpty() && constraints.isEmpty()) {
            return DependencyPolicySettings.defaults();
        }
        return new DependencyPolicySettings(exclusions, constraints);
    }

    static void write(StringBuilder toml, DependencyPolicySettings policy) {
        if (policy == null || policy.equals(DependencyPolicySettings.defaults())) {
            return;
        }
        if (!policy.exclusions().isEmpty()) {
            toml.append("[dependencyPolicy]\n");
            toml.append("exclude = [");
            for (int index = 0; index < policy.exclusions().size(); index++) {
                if (index > 0) {
                    toml.append(", ");
                }
                toml.append(policyExclusion(policy.exclusions().get(index)));
            }
            toml.append("]\n\n");
        }
        if (!policy.constraints().isEmpty()) {
            toml.append("[dependencyConstraints]\n");
            for (DependencyConstraint constraint : new TreeMap<>(policy.constraints()).values()) {
                toml.append(quote(constraint.coordinate())).append(" = { ");
                constraint.versionRef()
                        .ifPresentOrElse(
                                versionRef -> toml.append("versionRef = ").append(quote(versionRef)),
                                () -> toml.append("version = ").append(quote(constraint.version())));
                toml.append(", kind = ").append(quote(constraint.kind().configValue()));
                constraint.reason().ifPresent(reason -> toml.append(", reason = ").append(quote(reason)));
                toml.append(" }\n");
            }
            toml.append('\n');
        }
    }

    private static List<DependencyPolicyExclusion> dependencyPolicyExclusions(TomlTable table) {
        if (table == null) {
            return List.of();
        }
        TomlValidation.validateKeysWithVersionRefHint("dependencyPolicy", table, DEPENDENCY_POLICY_KEYS);
        Object rawExclusions = table.get(List.of("exclude"));
        if (rawExclusions == null) {
            return List.of();
        }
        if (!(rawExclusions instanceof TomlArray array)) {
            throw new ZoltConfigException(
                    "Invalid value for [dependencyPolicy].exclude in zolt.toml. Use an array of { group, artifact, reason } tables.");
        }
        List<DependencyPolicyExclusion> exclusions = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            TomlTable exclusion = array.getTable(index);
            if (exclusion == null) {
                throw new ZoltConfigException(
                        "Invalid value for [dependencyPolicy].exclude[" + index + "] in zolt.toml. Use { group = \"...\", artifact = \"...\" }.");
            }
            String section = "dependencyPolicy.exclude[" + index + "]";
            TomlValidation.validateKeysWithVersionRefHint(section, exclusion, DEPENDENCY_POLICY_EXCLUSION_KEYS);
            exclusions.add(new DependencyPolicyExclusion(
                    requiredString(exclusion, section, "group"),
                    requiredString(exclusion, section, "artifact"),
                    optionalString(exclusion, section, "reason")));
        }
        return List.copyOf(exclusions);
    }

    private static Map<String, DependencyConstraint> dependencyConstraints(
            TomlTable table,
            Map<String, String> versionAliases) {
        if (table == null) {
            return Map.of();
        }
        Map<String, DependencyConstraint> constraints = new LinkedHashMap<>();
        for (String coordinate : table.keySet()) {
            validatePackageCoordinate("dependencyConstraints", coordinate);
            TomlTable constraintTable = table.getTable(List.of(coordinate));
            if (constraintTable == null) {
                throw new ZoltConfigException(
                        "Invalid value for [dependencyConstraints]."
                                + coordinate
                                + " in zolt.toml. Use { version = \"...\", kind = \"strict\" }.");
            }
            TomlValidation.validateKeysWithVersionRefHint(
                    "dependencyConstraints." + coordinate,
                    constraintTable,
                    DEPENDENCY_CONSTRAINT_KEYS);
            Optional<String> version = optionalVersionOrRef(
                    constraintTable,
                    "dependencyConstraints." + coordinate,
                    VersionPolicy.Context.CONSTRAINT,
                    versionAliases);
            Optional<String> versionRef = optionalVersionRef(
                    constraintTable,
                    "dependencyConstraints." + coordinate,
                    versionAliases);
            String kindValue = stringOrDefault(
                    constraintTable,
                    "dependencyConstraints." + coordinate,
                    "kind",
                    DependencyConstraintKind.STRICT.configValue());
            DependencyConstraintKind kind = DependencyConstraintKind.fromConfigValue(kindValue)
                    .orElseThrow(() -> new ZoltConfigException(
                            "Unsupported dependency constraint kind `"
                                    + kindValue
                                    + "` in zolt.toml. Supported dependency constraint kinds are: "
                                    + DependencyConstraintKind.supportedValues()
                                    + "."));
            constraints.put(coordinate, new DependencyConstraint(
                    coordinate,
                    version.orElseThrow(() -> new ZoltConfigException(
                            "Missing required field [dependencyConstraints."
                                    + coordinate
                                    + "].version in zolt.toml. Add version or versionRef.")),
                    versionRef,
                    kind,
                    optionalString(constraintTable, "dependencyConstraints." + coordinate, "reason")));
        }
        return constraints;
    }

    private static String policyExclusion(DependencyPolicyExclusion exclusion) {
        List<String> parts = new ArrayList<>();
        parts.add("group = " + quote(exclusion.group()));
        parts.add("artifact = " + quote(exclusion.artifact()));
        exclusion.reason().ifPresent(reason -> parts.add("reason = " + quote(reason)));
        return "{ " + String.join(", ", parts) + " }";
    }

    private static void validatePackageCoordinate(String section, String coordinate) {
        if (coordinate == null || coordinate.isBlank() || !coordinate.equals(coordinate.trim())) {
            throw new ZoltConfigException(
                    "Invalid coordinate in ["
                            + section
                            + "]. Use `group:artifact` without leading or trailing whitespace.");
        }
        for (int index = 0; index < coordinate.length(); index++) {
            if (Character.isWhitespace(coordinate.charAt(index))) {
                throw new ZoltConfigException(
                        "Invalid coordinate `"
                                + coordinate
                                + "` in ["
                                + section
                                + "]. Use `group:artifact` without whitespace.");
            }
        }
        String[] parts = coordinate.split(":", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ZoltConfigException(
                    "Invalid coordinate `"
                            + coordinate
                            + "` in ["
                            + section
                            + "]. Use `group:artifact`.");
        }
    }

    private static String requiredString(TomlTable table, String section, String key) {
        String value = table.getString(key);
        if (value == null || value.isBlank()) {
            throw new ZoltConfigException(
                    "Missing required field [" + section + "]." + key + " in zolt.toml. Add a non-empty string value.");
        }
        return value;
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

    private static Optional<String> optionalVersionOrRef(
            TomlTable table,
            String section,
            VersionPolicy.Context versionContext,
            Map<String, String> versionAliases) {
        Object rawVersion = table.get(List.of("version"));
        Object rawVersionRef = table.get(List.of("versionRef"));
        if (rawVersion != null && rawVersionRef != null) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "] in zolt.toml. Use either version or versionRef, not both.");
        }
        if (rawVersion == null && rawVersionRef == null) {
            return Optional.empty();
        }
        if (rawVersion instanceof String version && !version.isBlank()) {
            validateVersion(versionContext, section, version);
            return Optional.of(version);
        }
        if (rawVersion != null) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "].version in zolt.toml. Use a non-empty string version.");
        }
        return Optional.of(requiredVersionRef(table, section, versionAliases));
    }

    private static Optional<String> optionalVersionRef(
            TomlTable table,
            String section,
            Map<String, String> versionAliases) {
        Object rawVersionRef = table.get(List.of("versionRef"));
        if (rawVersionRef == null) {
            return Optional.empty();
        }
        requiredVersionRef(table, section, versionAliases);
        return Optional.of((String) rawVersionRef);
    }

    private static void validateVersion(
            VersionPolicy.Context context,
            String subject,
            String version) {
        VersionPolicy.violation(context, version).ifPresent(violation -> {
            throw new ZoltConfigException(
                    "Invalid "
                            + context.description()
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
        validateVersionAliasName(alias);
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

    private static void validateVersionAliasName(String alias) {
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
