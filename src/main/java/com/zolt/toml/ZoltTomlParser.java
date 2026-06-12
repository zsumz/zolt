package com.zolt.toml;

import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyConstraintKind;
import com.zolt.project.DependencyExclusionSpec;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencyPolicyExclusion;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.NativeSettings;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.RepositoryCredentialSettings;
import com.zolt.project.RepositorySettings;
import com.zolt.project.VersionAliasRules;
import com.zolt.project.VersionPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public final class ZoltTomlParser {
    private static final Set<String> TOP_LEVEL_SECTIONS = Set.of(
            "project",
            "repositories",
            "repositoryCredentials",
            "versions",
            "platforms",
            "dependencyPolicy",
            "dependencyConstraints",
            "api",
            "dependencies",
            "runtime",
            "provided",
            "dev",
            "annotationProcessors",
            "test",
            "build",
            "resources",
            "generated",
            "compiler",
            "package",
            "publish",
            "framework",
            "native");
    private static final Set<String> TEST_KEYS = Set.of("dependencies", "sources", "annotationProcessors", "runtime");
    private static final Set<String> DEPENDENCY_POLICY_KEYS = Set.of("exclude");
    private static final Set<String> DEPENDENCY_POLICY_EXCLUSION_KEYS = Set.of("group", "artifact", "reason");
    private static final Set<String> DEPENDENCY_CONSTRAINT_KEYS = Set.of("version", "versionRef", "kind", "reason");

    public ProjectConfig parse(Path path) {
        try {
            return parse(Toml.parse(path));
        } catch (IOException exception) {
            throw new ZoltConfigException(
                    "Could not read zolt.toml at " + path + ". Check that the file exists and is readable.");
        }
    }

    public ProjectConfig parse(String content) {
        return parse(Toml.parse(content));
    }

    private ProjectConfig parse(TomlParseResult result) {
        if (result.hasErrors()) {
            throw new ZoltConfigException(parseErrorMessage(result));
        }

        validateTopLevelSections(result);

        ProjectMetadata project = ProjectSectionCodec.parse(result);

        Map<String, RepositorySettings> repositorySettings =
                RepositorySectionCodec.repositorySettings(optionalTable(result, "repositories"));
        if (repositorySettings.isEmpty()) {
            repositorySettings = ProjectConfig.defaultRepositorySettings();
        }
        Map<String, String> repositories = RepositorySectionCodec.repositoryUrls(repositorySettings);
        Map<String, RepositoryCredentialSettings> repositoryCredentials =
                RepositorySectionCodec.repositoryCredentials(optionalTable(result, "repositoryCredentials"));
        RepositorySectionCodec.validateRepositoryCredentialReferences(repositorySettings, repositoryCredentials);

        Map<String, String> versionAliases = VersionAliasSectionCodec.parse(optionalTable(result, "versions"));
        Map<String, DependencyMetadata> dependencyMetadata = new LinkedHashMap<>();
        Map<String, String> platforms =
                PlatformSectionCodec.parse(optionalTable(result, "platforms"), versionAliases, dependencyMetadata);
        DependencyPolicySettings dependencyPolicy = dependencyPolicy(
                optionalTable(result, "dependencyPolicy"),
                optionalTable(result, "dependencyConstraints"),
                versionAliases);

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

        DependencyDeclarations dependencies = dependencyDeclarations(
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
                dependencies,
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

        BuildSettings build = BuildSectionCodec.parseBuild(optionalTable(result, "build"));
        build = BuildSectionCodec.parseTestSources(testTable, build);
        build = BuildSectionCodec.parseTestRuntime(testTable, build);
        build = BuildSectionCodec.parseResourceRoots(optionalTable(result, "resources"), build);
        build = GeneratedSectionCodec.parse(optionalTable(result, "generated"), build, versionAliases);
        CompilerSettings compilerSettings = CompilerSectionCodec.parse(optionalTable(result, "compiler"));
        PackageSettings packageSettings = PackageSectionCodec.parse(optionalTable(result, "package"));
        FrameworkSettings frameworkSettings = FrameworkSectionCodec.parse(optionalTable(result, "framework"));
        NativeSettings nativeSettings = NativeSectionCodec.parse(optionalTable(result, "native"), project.name());

        return new ProjectConfig(
                project,
                repositories,
                repositorySettings,
                repositoryCredentials,
                versionAliases,
                platforms,
                apiDependencies.versioned(),
                apiDependencies.managed(),
                apiDependencies.workspace(),
                dependencies.versioned(),
                dependencies.managed(),
                dependencies.workspace(),
                runtimeDependencies.versioned(),
                runtimeDependencies.managed(),
                providedDependencies.versioned(),
                providedDependencies.managed(),
                devDependencies.versioned(),
                devDependencies.managed(),
                testDependencies.versioned(),
                testDependencies.managed(),
                testDependencies.workspace(),
                annotationProcessors.versioned(),
                annotationProcessors.managed(),
                testAnnotationProcessors.versioned(),
                testAnnotationProcessors.managed(),
                dependencyPolicy,
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                frameworkSettings,
                dependencyMetadata);
    }

    private static String parseErrorMessage(TomlParseResult result) {
        TomlParseError firstError = result.errors().getFirst();
        return "Could not parse zolt.toml. Fix the TOML syntax near "
                + firstError.position()
                + ": "
                + firstError.getMessage();
    }

    private static void validateTopLevelSections(TomlParseResult result) {
        for (String key : result.keySet()) {
            if (!TOP_LEVEL_SECTIONS.contains(key)) {
                throw new ZoltConfigException(
                        "Unknown top-level section [" + key + "] in zolt.toml. Remove it or check the spelling.");
            }
        }
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

    private static DependencyPolicySettings dependencyPolicy(
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

    private static List<DependencyPolicyExclusion> dependencyPolicyExclusions(TomlTable policyTable) {
        if (policyTable == null) {
            return List.of();
        }
        validateKeys("dependencyPolicy", policyTable, DEPENDENCY_POLICY_KEYS);
        Object rawExclusions = policyTable.get(List.of("exclude"));
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
            validateKeys(section, exclusion, DEPENDENCY_POLICY_EXCLUSION_KEYS);
            exclusions.add(new DependencyPolicyExclusion(
                    requiredString(exclusion, section, "group"),
                    requiredString(exclusion, section, "artifact"),
                    optionalString(exclusion, section, "reason")));
        }
        return List.copyOf(exclusions);
    }

    private static Map<String, DependencyConstraint> dependencyConstraints(
            TomlTable constraintsTable,
            Map<String, String> versionAliases) {
        if (constraintsTable == null) {
            return Map.of();
        }
        Map<String, DependencyConstraint> constraints = new LinkedHashMap<>();
        for (String coordinate : constraintsTable.keySet()) {
            validatePackageCoordinate("dependencyConstraints", coordinate);
            TomlTable constraintTable = constraintsTable.getTable(List.of(coordinate));
            if (constraintTable == null) {
                throw new ZoltConfigException(
                        "Invalid value for [dependencyConstraints]."
                                + coordinate
                                + " in zolt.toml. Use { version = \"...\", kind = \"strict\" }.");
            }
            validateKeys("dependencyConstraints." + coordinate, constraintTable, DEPENDENCY_CONSTRAINT_KEYS);
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

    private static void validateNoDuplicateMainDependencyCoordinates(
            DependencyDeclarations apiDependencies,
            DependencyDeclarations implementationDependencies) {
        Set<String> apiCoordinates = allCoordinates(apiDependencies);
        for (String coordinate : allCoordinates(implementationDependencies)) {
            if (apiCoordinates.contains(coordinate)) {
                throw new ZoltConfigException(
                        "Dependency "
                                + coordinate
                                + " is declared in both [api.dependencies] and [dependencies]. Keep it in one section.");
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

        ArrayList<String> values = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            String value = array.getString(index);
            if (value == null || value.isBlank()) {
                throw new ZoltConfigException(
                        "Invalid value for [" + section + "]." + key + "[" + index + "] in zolt.toml. Use a non-empty string.");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private record DependencyDeclarations(
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace) {
        private static DependencyDeclarations empty() {
            return new DependencyDeclarations(Map.of(), Set.of(), Map.of());
        }
    }

}
