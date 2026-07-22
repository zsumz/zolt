package sh.zolt.toml.dependency;

import static sh.zolt.toml.dependency.DependencySectionDuplicateValidator.validateNoDuplicateMainDependencyCoordinates;

import sh.zolt.project.DependencyExclusionSpec;
import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.VersionPolicy;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.support.TomlVersions;
import sh.zolt.toml.support.TomlValidation;
import sh.zolt.toml.support.TomlScalars;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public final class DependencySectionCodec {
    private static final Set<String> TEST_KEYS = Set.of("dependencies", "sources", "annotationProcessors", "runtime", "suites");

    private DependencySectionCodec() {
    }

    public static ParsedDependencies parse(
            TomlParseResult result,
            Map<String, DependencyMetadata> dependencyMetadata,
            Map<String, String> versionAliases) {
        TomlTable apiTable = optionalTable(result, "api");
        DependencyDeclarations apiDependencies = DependencyDeclarations.empty();
        if (apiTable != null) {
            TomlValidation.validateKeysWithVersionRefHint("api", apiTable, Set.of("dependencies"));
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
            TomlValidation.validateKeysWithVersionRefHint("runtime", runtimeTable, Set.of("dependencies"));
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
            TomlValidation.validateKeysWithVersionRefHint("provided", providedTable, Set.of("dependencies"));
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
            TomlValidation.validateKeysWithVersionRefHint("dev", devTable, Set.of("dependencies"));
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
                true,
                versionAliases);

        TomlTable testTable = optionalTable(result, "test");
        DependencyDeclarations testDependencies = DependencyDeclarations.empty();
        DependencyDeclarations testAnnotationProcessors = DependencyDeclarations.empty();
        if (testTable != null) {
            TomlValidation.validateKeysWithVersionRefHint("test", testTable, TEST_KEYS);
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
                    true,
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

    public static void write(StringBuilder toml, ProjectConfig config) {
        DependencySectionWriter.write(toml, config);
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
                TomlVersions.validateVersion(VersionPolicy.Context.EXTERNAL_DEPENDENCY, section + "." + key, value);
                versioned.put(key, value);
                continue;
            }
            if (rawValue instanceof TomlTable dependencyTable) {
                TomlValidation.validateKeysWithVersionRefHint(section + "." + key, dependencyTable, allowWorkspace
                        ? Set.of("version", "versionRef", "workspace", "optional", "publishOnly", "exclusions",
                                "classifier", "type")
                        : Set.of("version", "versionRef", "optional", "publishOnly", "exclusions",
                                "classifier", "type"));
                Object rawVersion = dependencyTable.get(List.of("version"));
                Object rawVersionRef = dependencyTable.get(List.of("versionRef"));
                Object rawWorkspace = dependencyTable.get(List.of("workspace"));
                boolean optional =
                        TomlScalars.booleanOrDefault(dependencyTable, section + "." + key, "optional", false);
                boolean publishOnly =
                        TomlScalars.booleanOrDefault(dependencyTable, section + "." + key, "publishOnly", false);
                List<DependencyExclusionSpec> exclusions = dependencyExclusions(dependencyTable, section + "." + key);
                String classifier =
                        TomlScalars.nonBlankStringOrDefault(dependencyTable, section + "." + key, "classifier", null);
                String type = TomlScalars.nonBlankStringOrDefault(dependencyTable, section + "." + key, "type", null);
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
                if (rawWorkspace != null && (classifier != null || type != null)) {
                    throw new ZoltConfigException(
                            "Invalid value for [" + section + "]." + key + " in zolt.toml. Classifier and type apply to external dependency artifacts, not workspace dependencies.");
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
                    version = TomlVersions.optionalVersionOrRef(
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
                        exclusions,
                        classifier,
                        type);
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
            if (!(array.get(index) instanceof TomlTable exclusion)) {
                throw new ZoltConfigException(
                        "Invalid value for [" + section + "].exclusions[" + index + "] in zolt.toml. Use { group = \"...\", artifact = \"...\" }.");
            }
            TomlValidation.validateKeysWithVersionRefHint(
                    section + ".exclusions[" + index + "]",
                    exclusion,
                    Set.of("group", "artifact"));
            exclusions.add(new DependencyExclusionSpec(
                    TomlScalars.requiredString(exclusion, section + ".exclusions[" + index + "]", "group"),
                    TomlScalars.requiredString(exclusion, section + ".exclusions[" + index + "]", "artifact")));
        }
        return List.copyOf(exclusions);
    }

    private static TomlTable optionalTable(TomlParseResult result, String section) {
        return result.getTable(section);
    }

    private static TomlTable optionalTable(TomlTable table, String key) {
        return table.getTable(List.of(key));
    }

    public record ParsedDependencies(
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

    public record DependencyDeclarations(
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace) {
        static DependencyDeclarations empty() {
            return new DependencyDeclarations(Map.of(), Set.of(), Map.of());
        }
    }
}
