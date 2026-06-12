package com.zolt.toml;

import com.zolt.project.DependencyExclusionSpec;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.ProjectConfig;
import com.zolt.project.VersionAliasRules;
import com.zolt.project.VersionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

final class DependencySectionCodec {
    private static final Set<String> TEST_KEYS = Set.of("dependencies", "sources", "annotationProcessors", "runtime");

    private DependencySectionCodec() {
    }

    static ParsedDependencies parse(
            TomlParseResult result,
            Map<String, DependencyMetadata> dependencyMetadata,
            Map<String, String> versionAliases) {
        TomlTable apiTable = optionalTable(result, "api");
        DependencyDeclarations apiDependencies = DependencyDeclarations.empty();
        if (apiTable != null) {
            validateKeys("api", apiTable, Set.of("dependencies"));
            apiDependencies = dependencyDeclarations(
                    optionalTable(apiTable, "dependencies"),
                    "api.dependencies",
                    dependencyMetadata,
                    true,
                    versionAliases);
        }

        DependencyDeclarations implementationDependencies = dependencyDeclarations(
                optionalTable(result, "dependencies"),
                "dependencies",
                dependencyMetadata,
                true,
                versionAliases);
        TomlTable runtimeTable = optionalTable(result, "runtime");
        DependencyDeclarations runtimeDependencies = DependencyDeclarations.empty();
        if (runtimeTable != null) {
            validateKeys("runtime", runtimeTable, Set.of("dependencies"));
            runtimeDependencies = dependencyDeclarations(
                    optionalTable(runtimeTable, "dependencies"),
                    "runtime.dependencies",
                    dependencyMetadata,
                    false,
                    versionAliases);
        }
        TomlTable providedTable = optionalTable(result, "provided");
        DependencyDeclarations providedDependencies = DependencyDeclarations.empty();
        if (providedTable != null) {
            validateKeys("provided", providedTable, Set.of("dependencies"));
            providedDependencies = dependencyDeclarations(
                    optionalTable(providedTable, "dependencies"),
                    "provided.dependencies",
                    dependencyMetadata,
                    false,
                    versionAliases);
        }
        TomlTable devTable = optionalTable(result, "dev");
        DependencyDeclarations devDependencies = DependencyDeclarations.empty();
        if (devTable != null) {
            validateKeys("dev", devTable, Set.of("dependencies"));
            devDependencies = dependencyDeclarations(
                    optionalTable(devTable, "dependencies"),
                    "dev.dependencies",
                    dependencyMetadata,
                    false,
                    versionAliases);
        }
        validateNoDuplicateMainDependencyCoordinates(
                apiDependencies,
                implementationDependencies,
                runtimeDependencies,
                providedDependencies,
                devDependencies);
        DependencyDeclarations annotationProcessors = dependencyDeclarations(
                optionalTable(result, "annotationProcessors"),
                "annotationProcessors",
                dependencyMetadata,
                false,
                versionAliases);

        TomlTable testTable = optionalTable(result, "test");
        DependencyDeclarations testDependencies = DependencyDeclarations.empty();
        DependencyDeclarations testAnnotationProcessors = DependencyDeclarations.empty();
        if (testTable != null) {
            validateKeys("test", testTable, TEST_KEYS);
            testDependencies = dependencyDeclarations(
                    optionalTable(testTable, "dependencies"),
                    "test.dependencies",
                    dependencyMetadata,
                    true,
                    versionAliases);
            testAnnotationProcessors = dependencyDeclarations(
                    optionalTable(testTable, "annotationProcessors"),
                    "test.annotationProcessors",
                    dependencyMetadata,
                    false,
                    versionAliases);
        }

        return new ParsedDependencies(
                apiDependencies,
                implementationDependencies,
                runtimeDependencies,
                providedDependencies,
                devDependencies,
                testDependencies,
                annotationProcessors,
                testAnnotationProcessors,
                dependencyMetadata);
    }

    static void write(StringBuilder toml, ProjectConfig config) {
        writeOptionalDependencies(
                toml,
                "api.dependencies",
                config.apiDependencies(),
                config.managedApiDependencies(),
                config.workspaceApiDependencies(),
                config.dependencyMetadata());
        writeDependencies(
                toml,
                "dependencies",
                config.dependencies(),
                config.managedDependencies(),
                config.workspaceDependencies(),
                config.dependencyMetadata());
        writeOptionalDependencies(
                toml,
                "runtime.dependencies",
                config.runtimeDependencies(),
                config.managedRuntimeDependencies(),
                config.dependencyMetadata());
        writeOptionalDependencies(
                toml,
                "provided.dependencies",
                config.providedDependencies(),
                config.managedProvidedDependencies(),
                config.dependencyMetadata());
        writeOptionalDependencies(
                toml,
                "dev.dependencies",
                config.devDependencies(),
                config.managedDevDependencies(),
                config.dependencyMetadata());
        writeDependencies(
                toml,
                "test.dependencies",
                config.testDependencies(),
                config.managedTestDependencies(),
                config.workspaceTestDependencies(),
                config.dependencyMetadata());
        writeOptionalDependencies(
                toml,
                "annotationProcessors",
                config.annotationProcessors(),
                config.managedAnnotationProcessors(),
                config.dependencyMetadata());
        writeOptionalDependencies(
                toml,
                "test.annotationProcessors",
                config.testAnnotationProcessors(),
                config.managedTestAnnotationProcessors(),
                config.dependencyMetadata());
    }

    private static DependencyDeclarations dependencyDeclarations(
            TomlTable table,
            String section,
            Map<String, DependencyMetadata> dependencyMetadata,
            boolean allowWorkspace,
            Map<String, String> versionAliases) {
        if (table == null) {
            return DependencyDeclarations.empty();
        }

        Map<String, String> versioned = new LinkedHashMap<>();
        LinkedHashSet<String> managed = new LinkedHashSet<>();
        Map<String, String> workspace = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            Object rawValue = table.get(List.of(key));
            if (rawValue instanceof String value) {
                if (value.isBlank()) {
                    throw new ZoltConfigException(
                            invalidDependencyDeclarationMessage(section, key, allowWorkspace));
                }
                validateVersion(VersionPolicy.Context.EXTERNAL_DEPENDENCY, section + "." + key, value);
                versioned.put(key, value);
                continue;
            }
            if (rawValue instanceof TomlTable dependencyTable) {
                validateKeys(section + "." + key, dependencyTable, allowWorkspace
                        ? Set.of("version", "versionRef", "workspace", "optional", "publishOnly", "exclusions")
                        : Set.of("version", "versionRef", "optional", "publishOnly", "exclusions"));
                Object rawVersion = dependencyTable.get(List.of("version"));
                Object rawVersionRef = dependencyTable.get(List.of("versionRef"));
                Object rawWorkspace = dependencyTable.get(List.of("workspace"));
                boolean optional = booleanOrDefault(dependencyTable, section + "." + key, "optional", false);
                boolean publishOnly = booleanOrDefault(dependencyTable, section + "." + key, "publishOnly", false);
                List<DependencyExclusionSpec> exclusions = dependencyExclusions(dependencyTable, section + "." + key);
                if ((rawVersion != null || rawVersionRef != null) && rawWorkspace != null) {
                    throw new ZoltConfigException(
                            "Invalid value for [" + section + "]." + key + " in zolt.toml. Use version, versionRef, or workspace; do not combine them.");
                }
                if (rawWorkspace != null && publishOnly) {
                    throw new ZoltConfigException(
                            "Invalid value for [" + section + "]." + key + ".publishOnly in zolt.toml. Publish-only dependencies must use an external version or platform-managed declaration, not workspace.");
                }
                if (rawWorkspace != null && !exclusions.isEmpty()) {
                    throw new ZoltConfigException(
                            "Invalid value for [" + section + "]." + key + ".exclusions in zolt.toml. Exclusions apply to external dependency edges, not workspace dependencies.");
                }
                String version = null;
                String versionRef = null;
                boolean managedDependency = false;
                String workspacePathValue = null;
                if (rawVersion == null && rawVersionRef == null && rawWorkspace == null) {
                    managedDependency = true;
                    if (!publishOnly) {
                        managed.add(key);
                    }
                } else if (rawVersion != null || rawVersionRef != null) {
                    version = optionalVersionOrRef(
                            dependencyTable,
                            section + "." + key,
                            VersionPolicy.Context.EXTERNAL_DEPENDENCY,
                            versionAliases)
                            .orElseThrow();
                    if (rawVersionRef instanceof String alias) {
                        versionRef = alias;
                    }
                    if (!publishOnly) {
                        versioned.put(key, version);
                    }
                } else if (rawWorkspace instanceof String workspacePath) {
                    if (workspacePath.isBlank()) {
                        throw new ZoltConfigException(
                                "Invalid value for [" + section + "]." + key + ".workspace in zolt.toml. Use a non-empty workspace member path.");
                    }
                    workspacePathValue = workspacePath;
                    workspace.put(key, workspacePath);
                } else {
                    throw new ZoltConfigException(
                            "Invalid value for [" + section + "]." + key + ".workspace in zolt.toml. Use a non-empty workspace member path.");
                }
                DependencyMetadata metadata = new DependencyMetadata(
                        section,
                        key,
                        version,
                        versionRef,
                        managedDependency,
                        workspacePathValue,
                        optional,
                        publishOnly,
                        exclusions);
                if (!metadata.emptyMetadata() || publishOnly) {
                    dependencyMetadata.put(DependencyMetadata.key(section, key), metadata);
                }
                continue;
            }
            throw new ZoltConfigException(
                    invalidDependencyDeclarationMessage(section, key, allowWorkspace));
        }
        return new DependencyDeclarations(versioned, Set.copyOf(managed), workspace);
    }

    private static void validateNoDuplicateMainDependencyCoordinates(
            DependencyDeclarations apiDependencies,
            DependencyDeclarations implementationDependencies,
            DependencyDeclarations runtimeDependencies,
            DependencyDeclarations providedDependencies,
            DependencyDeclarations devDependencies) {
        Map<String, String> sections = new LinkedHashMap<>();
        addCoordinates(sections, apiDependencies, "api.dependencies");
        addCoordinates(sections, implementationDependencies, "dependencies");
        addCoordinates(sections, runtimeDependencies, "runtime.dependencies");
        addCoordinates(sections, providedDependencies, "provided.dependencies");
        addCoordinates(sections, devDependencies, "dev.dependencies");
    }

    private static void addCoordinates(
            Map<String, String> sections,
            DependencyDeclarations declarations,
            String section) {
        for (String coordinate : allCoordinates(declarations)) {
            String existing = sections.putIfAbsent(coordinate, section);
            if (existing != null) {
                throw new ZoltConfigException(
                        "Dependency "
                                + coordinate
                                + " is declared in both ["
                                + existing
                                + "] and ["
                                + section
                                + "]. Keep it in one section.");
            }
        }
    }

    private static Set<String> allCoordinates(DependencyDeclarations declarations) {
        LinkedHashSet<String> coordinates = new LinkedHashSet<>();
        coordinates.addAll(declarations.versioned().keySet());
        coordinates.addAll(declarations.managed());
        coordinates.addAll(declarations.workspace().keySet());
        return Set.copyOf(coordinates);
    }

    private static String invalidDependencyDeclarationMessage(String section, String key, boolean allowWorkspace) {
        String allowedValues = allowWorkspace
                ? "Use a non-empty string version, { versionRef = \"alias\" }, {} for a platform-managed version, or { workspace = \"path\" } for a workspace member. Inline tables may also include optional, publishOnly, and exclusions metadata."
                : "Use a non-empty string version, { versionRef = \"alias\" }, or {} for a platform-managed version. Inline tables may also include optional, publishOnly, and exclusions metadata.";
        return "Invalid value for [" + section + "]." + key + " in zolt.toml. " + allowedValues;
    }

    private static List<DependencyExclusionSpec> dependencyExclusions(TomlTable table, String section) {
        Object rawValue = table.get(List.of("exclusions"));
        if (rawValue == null) {
            return List.of();
        }
        if (!(rawValue instanceof TomlArray array)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "].exclusions in zolt.toml. Use an array of { group, artifact } tables.");
        }
        List<DependencyExclusionSpec> exclusions = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            TomlTable exclusion = array.getTable(index);
            if (exclusion == null) {
                throw new ZoltConfigException(
                        "Invalid value for [" + section + "].exclusions[" + index + "] in zolt.toml. Use { group = \"...\", artifact = \"...\" }.");
            }
            validateKeys(section + ".exclusions[" + index + "]", exclusion, Set.of("group", "artifact"));
            exclusions.add(new DependencyExclusionSpec(
                    requiredString(exclusion, section + ".exclusions[" + index + "]", "group"),
                    requiredString(exclusion, section + ".exclusions[" + index + "]", "artifact")));
        }
        return List.copyOf(exclusions);
    }

    private static void writeDependencies(
            StringBuilder toml,
            String section,
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace,
            Map<String, DependencyMetadata> dependencyMetadata) {
        toml.append('[').append(section).append("]\n");
        for (String coordinate : sortedCoordinates(versioned, managed, workspace, dependencyMetadata, section)) {
            toml.append(quote(coordinate)).append(" = ");
            DependencyMetadata metadata = dependencyMetadata.get(DependencyMetadata.key(section, coordinate));
            if (metadata != null && (!metadata.emptyMetadata() || metadata.publishOnly())) {
                writeDependencyMetadata(toml, coordinate, versioned, managed, workspace, metadata);
            } else {
                writeSimpleDependency(toml, coordinate, versioned, workspace);
            }
            toml.append('\n');
        }
        toml.append('\n');
    }

    private static void writeOptionalDependencies(
            StringBuilder toml,
            String section,
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, DependencyMetadata> dependencyMetadata) {
        if (versioned.isEmpty() && managed.isEmpty() && !hasDependencyMetadata(dependencyMetadata, section)) {
            return;
        }
        writeDependencies(toml, section, versioned, managed, Map.of(), dependencyMetadata);
    }

    private static void writeOptionalDependencies(
            StringBuilder toml,
            String section,
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace,
            Map<String, DependencyMetadata> dependencyMetadata) {
        if (versioned.isEmpty()
                && managed.isEmpty()
                && workspace.isEmpty()
                && !hasDependencyMetadata(dependencyMetadata, section)) {
            return;
        }
        writeDependencies(toml, section, versioned, managed, workspace, dependencyMetadata);
    }

    private static void writeSimpleDependency(
            StringBuilder toml,
            String coordinate,
            Map<String, String> versioned,
            Map<String, String> workspace) {
        String workspacePath = workspace.get(coordinate);
        if (workspacePath != null) {
            toml.append("{ workspace = ").append(quote(workspacePath)).append(" }");
            return;
        }
        String version = versioned.get(coordinate);
        if (version == null) {
            toml.append("{}");
        } else {
            toml.append(quote(version));
        }
    }

    private static void writeDependencyMetadata(
            StringBuilder toml,
            String coordinate,
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace,
            DependencyMetadata metadata) {
        List<String> parts = new ArrayList<>();
        String version = metadata.version() == null ? versioned.get(coordinate) : metadata.version();
        String workspacePath = metadata.workspace() == null ? workspace.get(coordinate) : metadata.workspace();
        boolean managedDependency = metadata.managed() || managed.contains(coordinate);
        if (metadata.versionRef() != null) {
            parts.add("versionRef = " + quote(metadata.versionRef()));
        } else if (version != null) {
            parts.add("version = " + quote(version));
        } else if (workspacePath != null) {
            parts.add("workspace = " + quote(workspacePath));
        } else if (!managedDependency) {
            parts.add("version = " + quote(""));
        }
        if (metadata.optional()) {
            parts.add("optional = true");
        }
        if (metadata.publishOnly()) {
            parts.add("publishOnly = true");
        }
        if (!metadata.exclusions().isEmpty()) {
            parts.add("exclusions = [" + exclusions(metadata.exclusions()) + "]");
        }
        toml.append("{ ").append(String.join(", ", parts)).append(" }");
    }

    private static String exclusions(List<DependencyExclusionSpec> exclusions) {
        return exclusions.stream()
                .map(exclusion -> "{ group = "
                        + quote(exclusion.group())
                        + ", artifact = "
                        + quote(exclusion.artifact())
                        + " }")
                .collect(Collectors.joining(", "));
    }

    private static boolean hasDependencyMetadata(Map<String, DependencyMetadata> dependencyMetadata, String section) {
        return dependencyMetadata.values().stream().anyMatch(metadata -> metadata.section().equals(section));
    }

    private static Set<String> sortedCoordinates(
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace,
            Map<String, DependencyMetadata> dependencyMetadata,
            String section) {
        TreeSet<String> coordinates = new TreeSet<>();
        coordinates.addAll(versioned.keySet());
        coordinates.addAll(managed);
        coordinates.addAll(workspace.keySet());
        dependencyMetadata.values().stream()
                .filter(metadata -> metadata.section().equals(section))
                .map(DependencyMetadata::coordinate)
                .forEach(coordinates::add);
        return coordinates;
    }

    private static void validateKeys(String section, TomlTable table, Set<String> allowedKeys) {
        for (String key : table.keySet()) {
            if (!allowedKeys.contains(key)) {
                if ("versionRef".equals(key)) {
                    throw new ZoltConfigException(
                            "Invalid value for ["
                                    + section
                                    + "].versionRef in zolt.toml. versionRef is only supported for dependency, platform, constraint, and tool artifact versions.");
                }
                throw new ZoltConfigException(
                        "Unknown field [" + section + "]." + key + " in zolt.toml. Remove it or check the spelling.");
            }
        }
    }

    private static TomlTable optionalTable(TomlParseResult result, String section) {
        return result.getTable(section);
    }

    private static TomlTable optionalTable(TomlTable table, String key) {
        return table.getTable(List.of(key));
    }

    private static String requiredString(TomlTable table, String section, String key) {
        String value = table.getString(key);
        if (value == null || value.isBlank()) {
            throw new ZoltConfigException(
                    "Missing required field [" + section + "]." + key + " in zolt.toml. Add a non-empty string value.");
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

    record ParsedDependencies(
            DependencyDeclarations apiDependencies,
            DependencyDeclarations implementationDependencies,
            DependencyDeclarations runtimeDependencies,
            DependencyDeclarations providedDependencies,
            DependencyDeclarations devDependencies,
            DependencyDeclarations testDependencies,
            DependencyDeclarations annotationProcessors,
            DependencyDeclarations testAnnotationProcessors,
            Map<String, DependencyMetadata> dependencyMetadata) {
    }

    record DependencyDeclarations(
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace) {
        private static DependencyDeclarations empty() {
            return new DependencyDeclarations(Map.of(), Set.of(), Map.of());
        }
    }
}
