package com.zolt.toml;

import com.zolt.project.BuildSettings;
import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyConstraintKind;
import com.zolt.project.DependencyExclusionSpec;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencyPolicyExclusion;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.NativeSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.PublicationMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import com.zolt.project.RepositoryCredentialSettings;
import com.zolt.project.RepositorySettings;
import com.zolt.project.ResourceFilteringSettings;
import com.zolt.project.ResourceMissingTokenPolicy;
import com.zolt.project.ResourceTokenSettings;
import com.zolt.project.TestRuntimeSettings;
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
    private static final Set<String> PROJECT_KEYS = Set.of("name", "version", "group", "java", "main");
    private static final Set<String> BUILD_KEYS = Set.of("source", "test", "output", "testOutput", "metadata");
    private static final Set<String> TEST_KEYS = Set.of("dependencies", "sources", "annotationProcessors", "runtime");
    private static final Set<String> TEST_RUNTIME_KEYS =
            Set.of("jvmArgs", "systemProperties", "environment", "events");
    private static final Set<String> BUILD_METADATA_KEYS = Set.of("buildInfo", "git", "reproducible");
    private static final Set<String> RESOURCES_KEYS = Set.of("main", "test", "filtering", "tokens");
    private static final Set<String> RESOURCE_FILTERING_KEYS = Set.of("enabled", "test", "includes", "missing");
    private static final Set<String> RESOURCE_TOKEN_KEYS = Set.of("value", "env", "project");
    private static final Set<String> REPOSITORY_KEYS = Set.of("url", "credentials");
    private static final Set<String> REPOSITORY_CREDENTIAL_KEYS = Set.of("usernameEnv", "passwordEnv");
    private static final Set<String> DEPENDENCY_POLICY_KEYS = Set.of("exclude");
    private static final Set<String> DEPENDENCY_POLICY_EXCLUSION_KEYS = Set.of("group", "artifact", "reason");
    private static final Set<String> DEPENDENCY_CONSTRAINT_KEYS = Set.of("version", "versionRef", "kind", "reason");
    private static final Set<String> GENERATED_KEYS = Set.of("main", "test");
    private static final Set<String> GENERATED_SOURCE_KEYS =
            Set.of("kind", "language", "output", "inputs", "required", "clean");
    private static final Set<String> COMPILER_KEYS = Set.of(
            "generatedSources",
            "generatedTestSources",
            "release",
            "encoding",
            "args",
            "testArgs");
    private static final Set<String> ZOLT_OWNED_JAVAC_ARGS = Set.of(
            "--release",
            "-source",
            "--source",
            "-target",
            "--target",
            "-encoding",
            "-d",
            "-classpath",
            "--class-path",
            "-cp",
            "-processorpath",
            "-processorpath:",
            "--processor-path",
            "-processor",
            "-s",
            "-sourcepath",
            "--source-path");
    private static final Set<String> PACKAGE_KEYS = Set.of("mode", "sources", "javadoc", "tests", "metadata", "manifest");
    private static final Set<String> PACKAGE_METADATA_KEYS = Set.of(
            "name",
            "description",
            "url",
            "license",
            "developers",
            "scm",
            "issues");
    private static final Set<String> FRAMEWORK_KEYS = Set.of("quarkus");
    private static final Set<String> QUARKUS_KEYS = Set.of("enabled", "package");
    private static final Set<String> NATIVE_KEYS = Set.of("imageName", "output", "args");

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

        TomlTable projectTable = requiredTable(result, "project");
        validateKeys("project", projectTable, PROJECT_KEYS);

        ProjectMetadata project = new ProjectMetadata(
                requiredString(projectTable, "project", "name"),
                requiredString(projectTable, "project", "version"),
                requiredString(projectTable, "project", "group"),
                requiredString(projectTable, "project", "java"),
                optionalString(projectTable, "project", "main"));

        Map<String, RepositorySettings> repositorySettings =
                repositorySettings(optionalTable(result, "repositories"));
        if (repositorySettings.isEmpty()) {
            repositorySettings = ProjectConfig.defaultRepositorySettings();
        }
        Map<String, String> repositories = repositoryUrls(repositorySettings);
        Map<String, RepositoryCredentialSettings> repositoryCredentials =
                repositoryCredentials(optionalTable(result, "repositoryCredentials"));
        validateRepositoryCredentialReferences(repositorySettings, repositoryCredentials);

        Map<String, String> versionAliases = versionAliases(optionalTable(result, "versions"));
        Map<String, String> platforms = versionDeclarations(
                optionalTable(result, "platforms"),
                "platforms",
                versionAliases);
        DependencyPolicySettings dependencyPolicy = dependencyPolicy(
                optionalTable(result, "dependencyPolicy"),
                optionalTable(result, "dependencyConstraints"),
                versionAliases);
        Map<String, DependencyMetadata> dependencyMetadata = new LinkedHashMap<>();

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

        BuildSettings build = parseBuild(optionalTable(result, "build"));
        build = parseTestSources(testTable, build);
        build = parseTestRuntime(testTable, build);
        build = parseResourceRoots(optionalTable(result, "resources"), build);
        build = parseGeneratedSources(optionalTable(result, "generated"), build);
        CompilerSettings compilerSettings = parseCompiler(optionalTable(result, "compiler"));
        PackageSettings packageSettings = parsePackage(optionalTable(result, "package"));
        FrameworkSettings frameworkSettings = parseFramework(optionalTable(result, "framework"));
        NativeSettings nativeSettings = parseNative(optionalTable(result, "native"), project.name());

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

    private static BuildSettings parseBuild(TomlTable buildTable) {
        BuildSettings defaults = BuildSettings.defaults();
        if (buildTable == null) {
            return defaults;
        }

        validateKeys("build", buildTable, BUILD_KEYS);
        BuildMetadataSettings metadata = parseBuildMetadata(optionalTable(buildTable, "metadata"));
        return new BuildSettings(
                stringOrDefault(buildTable, "build", "source", defaults.source()),
                stringOrDefault(buildTable, "build", "test", defaults.test()),
                stringOrDefault(buildTable, "build", "output", defaults.output()),
                stringOrDefault(buildTable, "build", "testOutput", defaults.testOutput()),
                defaults.testSources(),
                defaults.groovyTestSources(),
                defaults.resourceRoots(),
                defaults.testResourceRoots(),
                metadata);
    }

    private static BuildMetadataSettings parseBuildMetadata(TomlTable metadataTable) {
        BuildMetadataSettings defaults = BuildMetadataSettings.defaults();
        if (metadataTable == null) {
            return defaults;
        }
        validateKeys("build.metadata", metadataTable, BUILD_METADATA_KEYS);
        return new BuildMetadataSettings(
                booleanOrDefault(metadataTable, "build.metadata", "buildInfo", defaults.buildInfo()),
                booleanOrDefault(metadataTable, "build.metadata", "git", defaults.git()),
                booleanOrDefault(metadataTable, "build.metadata", "reproducible", defaults.reproducible()));
    }

    private static BuildSettings parseTestSources(TomlTable testTable, BuildSettings build) {
        if (testTable == null) {
            return build;
        }
        TomlTable sourcesTable = optionalTable(testTable, "sources");
        if (sourcesTable == null) {
            return build;
        }
        validateKeys("test.sources", sourcesTable, Set.of("java", "groovy"));
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

    private static BuildSettings parseTestRuntime(TomlTable testTable, BuildSettings build) {
        if (testTable == null) {
            return build;
        }
        TomlTable runtimeTable = optionalTable(testTable, "runtime");
        if (runtimeTable == null) {
            return build;
        }
        validateKeys("test.runtime", runtimeTable, TEST_RUNTIME_KEYS);
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

    private static BuildSettings parseResourceRoots(TomlTable resourcesTable, BuildSettings build) {
        if (resourcesTable == null) {
            return build;
        }
        validateKeys("resources", resourcesTable, RESOURCES_KEYS);
        return new BuildSettings(
                build.source(),
                build.test(),
                build.output(),
                build.testOutput(),
                build.testSources(),
                build.groovyTestSources(),
                stringListOrDefault(resourcesTable, "resources", "main", build.resourceRoots()),
                stringListOrDefault(resourcesTable, "resources", "test", build.testResourceRoots()),
                parseResourceFiltering(
                        optionalTable(resourcesTable, "filtering"),
                        optionalTable(resourcesTable, "tokens")),
                build.metadata());
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
        validateKeys("resources.filtering", filteringTable, RESOURCE_FILTERING_KEYS);
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

    private static Map<String, ResourceTokenSettings> resourceTokens(TomlTable tokensTable) {
        if (tokensTable == null) {
            return Map.of();
        }
        Map<String, ResourceTokenSettings> tokens = new LinkedHashMap<>();
        for (String key : tokensTable.keySet()) {
            if (key.isBlank()) {
                throw new ZoltConfigException(
                        "Invalid resource token name in [resources.tokens]. Use a non-empty token name.");
            }
            TomlTable tokenTable = tokensTable.getTable(List.of(key));
            if (tokenTable == null) {
                throw new ZoltConfigException(
                        "Invalid value for [resources.tokens]."
                                + key
                                + " in zolt.toml. Use exactly one of { value = \"...\" }, { env = \"...\" }, or { project = \"...\" }.");
            }
            validateKeys("resources.tokens." + key, tokenTable, RESOURCE_TOKEN_KEYS);
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

    private static BuildSettings parseGeneratedSources(TomlTable generatedTable, BuildSettings build) {
        if (generatedTable == null) {
            return build;
        }
        validateKeys("generated", generatedTable, GENERATED_KEYS);
        return build.withGeneratedSources(
                parseGeneratedSourceScope(optionalTable(generatedTable, "main"), "generated.main"),
                parseGeneratedSourceScope(optionalTable(generatedTable, "test"), "generated.test"));
    }

    private static List<GeneratedSourceStep> parseGeneratedSourceScope(TomlTable scopeTable, String section) {
        if (scopeTable == null) {
            return List.of();
        }
        List<GeneratedSourceStep> steps = new ArrayList<>();
        for (String id : scopeTable.keySet()) {
            TomlTable stepTable = optionalTable(scopeTable, id);
            if (stepTable == null) {
                throw new ZoltConfigException(
                        "Invalid value for [" + section + "]." + id + " in zolt.toml. Use a table with kind, language, output, and inputs.");
            }
            String stepSection = section + "." + id;
            validateKeys(stepSection, stepTable, GENERATED_SOURCE_KEYS);
            String kindValue = requiredString(stepTable, stepSection, "kind");
            GeneratedSourceKind kind = GeneratedSourceKind.fromConfigValue(kindValue)
                    .orElseThrow(() -> new ZoltConfigException(
                            "Unsupported generated source kind `"
                                    + kindValue
                                    + "` in zolt.toml. Supported generated source kinds are: "
                                    + GeneratedSourceKind.supportedValues()
                                    + "."));
            String language = requiredString(stepTable, stepSection, "language");
            if (!"java".equals(language)) {
                throw new ZoltConfigException(
                        "Unsupported generated source language `"
                                + language
                                + "` in zolt.toml. Supported generated source languages are: java.");
            }
            List<String> inputs = stringListOrDefault(stepTable, stepSection, "inputs", List.of());
            if (inputs.isEmpty()) {
                throw new ZoltConfigException(
                        "Missing required field ["
                                + stepSection
                                + "].inputs in zolt.toml. Add at least one project-relative input path.");
            }
            steps.add(new GeneratedSourceStep(
                    id,
                    kind,
                    language,
                    requiredString(stepTable, stepSection, "output"),
                    inputs,
                    booleanOrDefault(stepTable, stepSection, "required", true),
                    booleanOrDefault(stepTable, stepSection, "clean", false)));
        }
        return List.copyOf(steps);
    }

    private static CompilerSettings parseCompiler(TomlTable compilerTable) {
        CompilerSettings defaults = CompilerSettings.defaults();
        if (compilerTable == null) {
            return defaults;
        }

        validateKeys("compiler", compilerTable, COMPILER_KEYS);
        List<String> args = stringListOrDefault(compilerTable, "compiler", "args", defaults.args());
        List<String> testArgs = stringListOrDefault(compilerTable, "compiler", "testArgs", defaults.testArgs());
        validateCompilerArgs("args", args);
        validateCompilerArgs("testArgs", testArgs);
        return new CompilerSettings(
                stringOrDefault(compilerTable, "compiler", "generatedSources", defaults.generatedSources()),
                stringOrDefault(
                        compilerTable,
                        "compiler",
                        "generatedTestSources",
                        defaults.generatedTestSources()),
                stringOrDefault(compilerTable, "compiler", "release", defaults.release()),
                stringOrDefault(compilerTable, "compiler", "encoding", defaults.encoding()),
                args,
                testArgs);
    }

    private static void validateCompilerArgs(String field, List<String> args) {
        for (String arg : args) {
            String flag = arg.contains("=") ? arg.substring(0, arg.indexOf('=')) : arg;
            if (ZOLT_OWNED_JAVAC_ARGS.contains(flag)) {
                throw new ZoltConfigException(
                        "Invalid value for [compiler]."
                                + field
                                + " in zolt.toml. Zolt owns `"
                                + flag
                                + "`; use [compiler].release, [compiler].encoding, source roots, dependencies, or annotation processor settings instead.");
            }
        }
    }

    private static PackageSettings parsePackage(TomlTable packageTable) {
        if (packageTable == null) {
            return PackageSettings.defaults();
        }

        validateKeys("package", packageTable, PACKAGE_KEYS);
        PackageSettings defaults = PackageSettings.defaults();
        PublicationMetadata metadata = parsePublicationMetadata(optionalTable(packageTable, "metadata"));
        Map<String, String> manifestAttributes = stringMap(optionalTable(packageTable, "manifest"), "package.manifest");
        Object rawMode = packageTable.get(List.of("mode"));
        PackageMode mode = defaults.mode();
        if (rawMode != null) {
            if (!(rawMode instanceof String modeValue) || modeValue.isBlank()) {
                throw new ZoltConfigException(
                        "Invalid value for [package].mode in zolt.toml. Use one of: "
                                + PackageMode.supportedValues()
                                + ".");
            }
            mode = PackageMode.fromConfigValue(modeValue).orElseThrow(() -> new ZoltConfigException(
                    "Unsupported package mode `"
                            + modeValue
                            + "` in zolt.toml. Supported package modes are: "
                            + PackageMode.supportedValues()
                            + "."));
        }
        return new PackageSettings(
                mode,
                booleanOrDefault(packageTable, "package", "sources", defaults.sources()),
                booleanOrDefault(packageTable, "package", "javadoc", defaults.javadoc()),
                booleanOrDefault(packageTable, "package", "tests", defaults.tests()),
                metadata,
                manifestAttributes);
    }

    private static PublicationMetadata parsePublicationMetadata(TomlTable metadataTable) {
        if (metadataTable == null) {
            return PublicationMetadata.empty();
        }
        validateKeys("package.metadata", metadataTable, PACKAGE_METADATA_KEYS);
        PublicationMetadata defaults = PublicationMetadata.empty();
        return new PublicationMetadata(
                stringOrDefault(metadataTable, "package.metadata", "name", defaults.name()),
                stringOrDefault(metadataTable, "package.metadata", "description", defaults.description()),
                stringOrDefault(metadataTable, "package.metadata", "url", defaults.url()),
                stringOrDefault(metadataTable, "package.metadata", "license", defaults.license()),
                stringListOrDefault(metadataTable, "package.metadata", "developers", defaults.developers()),
                stringOrDefault(metadataTable, "package.metadata", "scm", defaults.scm()),
                stringOrDefault(metadataTable, "package.metadata", "issues", defaults.issues()));
    }

    private static FrameworkSettings parseFramework(TomlTable frameworkTable) {
        if (frameworkTable == null) {
            return FrameworkSettings.defaults();
        }

        validateKeys("framework", frameworkTable, FRAMEWORK_KEYS);
        return new FrameworkSettings(parseQuarkus(optionalTable(frameworkTable, "quarkus")));
    }

    private static QuarkusSettings parseQuarkus(TomlTable quarkusTable) {
        QuarkusSettings defaults = QuarkusSettings.defaults();
        if (quarkusTable == null) {
            return defaults;
        }

        validateKeys("framework.quarkus", quarkusTable, QUARKUS_KEYS);
        boolean enabled = booleanOrDefault(quarkusTable, "framework.quarkus", "enabled", defaults.enabled());
        Object rawPackage = quarkusTable.get(List.of("package"));
        if (rawPackage == null) {
            return new QuarkusSettings(enabled, defaults.packageMode());
        }
        if (!(rawPackage instanceof String packageMode) || packageMode.isBlank()) {
            throw new ZoltConfigException(
                    "Invalid value for [framework.quarkus].package in zolt.toml. Use one of: "
                            + QuarkusPackageMode.supportedValues()
                            + ".");
        }
        return new QuarkusSettings(
                enabled,
                QuarkusPackageMode.fromConfigValue(packageMode).orElseThrow(() -> new ZoltConfigException(
                        "Unsupported Quarkus package mode `"
                                + packageMode
                                + "` in zolt.toml. Supported Quarkus package modes are: "
                                + QuarkusPackageMode.supportedValues()
                                + ".")));
    }

    private static NativeSettings parseNative(TomlTable nativeTable, String projectName) {
        if (nativeTable == null) {
            return NativeSettings.defaults();
        }

        NativeSettings defaults = NativeSettings.defaults().withDefaultImageName(projectName);
        validateKeys("native", nativeTable, NATIVE_KEYS);
        return new NativeSettings(
                stringOrDefault(nativeTable, "native", "imageName", defaults.imageName()),
                stringOrDefault(nativeTable, "native", "output", defaults.output()),
                stringListOrDefault(nativeTable, "native", "args", defaults.args()));
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

    private static TomlTable requiredTable(TomlParseResult result, String section) {
        TomlTable table = result.getTable(section);
        if (table == null) {
            throw new ZoltConfigException("Missing required section [" + section + "] in zolt.toml.");
        }
        return table;
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

    private static Map<String, String> versionAliases(TomlTable table) {
        if (table == null) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String alias : table.keySet()) {
            validateVersionAliasName(alias);
            Object rawValue = table.get(List.of(alias));
            if (!(rawValue instanceof String value) || value.isBlank()) {
                throw new ZoltConfigException(
                        "Invalid [versions]."
                                + alias
                                + " in zolt.toml. Use a non-empty literal version string; Zolt does not support interpolation or dynamic versions.");
            }
            if (!value.equals(value.trim())) {
                throw new ZoltConfigException(
                        "Invalid [versions]."
                                + alias
                                + " in zolt.toml. Version aliases must not contain leading or trailing whitespace.");
            }
            values.put(alias, value);
        }
        return values;
    }

    private static Map<String, String> versionDeclarations(
            TomlTable table,
            String section,
            Map<String, String> versionAliases) {
        if (table == null) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            Object rawValue = table.get(List.of(key));
            if (rawValue instanceof String value) {
                if (value.isBlank()) {
                    throw new ZoltConfigException(
                            "Invalid value for [" + section + "]." + key + " in zolt.toml. Use a non-empty version string or { versionRef = \"alias\" }.");
                }
                values.put(key, value);
                continue;
            }
            if (rawValue instanceof TomlTable valueTable) {
                validateKeys(section + "." + key, valueTable, Set.of("versionRef"));
                values.put(key, requiredVersionRef(valueTable, section + "." + key, versionAliases));
                continue;
            }
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use a non-empty version string or { versionRef = \"alias\" }.");
        }
        return values;
    }

    private static Optional<String> optionalVersionOrRef(
            TomlTable table,
            String section,
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
            return Optional.of(version);
        }
        if (rawVersion != null) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "].version in zolt.toml. Use a non-empty string version.");
        }
        return Optional.of(requiredVersionRef(table, section, versionAliases));
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
        if (alias == null || alias.isBlank() || !alias.equals(alias.trim())) {
            throw new ZoltConfigException(
                    "Invalid [versions] alias `"
                            + alias
                            + "`. Alias names may contain only letters, digits, dot, underscore, and hyphen.");
        }
        for (int index = 0; index < alias.length(); index++) {
            char character = alias.charAt(index);
            if (!Character.isLetterOrDigit(character)
                    && character != '.'
                    && character != '_'
                    && character != '-') {
                throw new ZoltConfigException(
                        "Invalid [versions] alias `"
                                + alias
                                + "`. Alias names may contain only letters, digits, dot, underscore, and hyphen.");
            }
        }
    }

    private static Map<String, RepositorySettings> repositorySettings(TomlTable table) {
        if (table == null) {
            return Map.of();
        }

        Map<String, RepositorySettings> values = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            Object rawValue = table.get(List.of(key));
            if (rawValue instanceof String value) {
                if (value.isBlank()) {
                    throw new ZoltConfigException(
                            "Invalid value for [repositories]." + key + " in zolt.toml. Use a non-empty URL string or { url = \"...\", credentials = \"...\" }.");
                }
                values.put(key, RepositorySettings.unauthenticated(key, value));
                continue;
            }
            if (rawValue instanceof TomlTable repositoryTable) {
                validateKeys("repositories." + key, repositoryTable, REPOSITORY_KEYS);
                String url = requiredString(repositoryTable, "repositories." + key, "url");
                Optional<String> credentials = optionalString(repositoryTable, "repositories." + key, "credentials");
                values.put(key, new RepositorySettings(key, url, credentials));
                continue;
            }
            throw new ZoltConfigException(
                    "Invalid value for [repositories]." + key + " in zolt.toml. Use a non-empty URL string or { url = \"...\", credentials = \"...\" }.");
        }
        return values;
    }

    private static Map<String, String> repositoryUrls(Map<String, RepositorySettings> repositorySettings) {
        Map<String, String> urls = new LinkedHashMap<>();
        for (Map.Entry<String, RepositorySettings> entry : repositorySettings.entrySet()) {
            urls.put(entry.getKey(), entry.getValue().url());
        }
        return urls;
    }

    private static Map<String, RepositoryCredentialSettings> repositoryCredentials(TomlTable table) {
        if (table == null) {
            return Map.of();
        }

        Map<String, RepositoryCredentialSettings> values = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            TomlTable credentialTable = table.getTable(List.of(key));
            if (credentialTable == null) {
                throw new ZoltConfigException(
                        "Invalid value for [repositoryCredentials]." + key + " in zolt.toml. Use a table with usernameEnv and passwordEnv.");
            }
            validateKeys("repositoryCredentials." + key, credentialTable, REPOSITORY_CREDENTIAL_KEYS);
            values.put(key, new RepositoryCredentialSettings(
                    key,
                    requiredString(credentialTable, "repositoryCredentials." + key, "usernameEnv"),
                    requiredString(credentialTable, "repositoryCredentials." + key, "passwordEnv")));
        }
        return values;
    }

    private static void validateRepositoryCredentialReferences(
            Map<String, RepositorySettings> repositories,
            Map<String, RepositoryCredentialSettings> credentials) {
        for (RepositorySettings repository : repositories.values()) {
            Optional<String> credentialId = repository.credentials();
            if (credentialId.isPresent() && !credentials.containsKey(credentialId.orElseThrow())) {
                throw new ZoltConfigException(
                        "Repository `"
                                + repository.id()
                                + "` references credentials `"
                                + credentialId.orElseThrow()
                                + "`, but [repositoryCredentials."
                                + credentialId.orElseThrow()
                                + "] is not defined.");
            }
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
                boolean managedDependency = false;
                String workspacePathValue = null;
                if (rawVersion == null && rawVersionRef == null && rawWorkspace == null) {
                    managedDependency = true;
                    if (!publishOnly) {
                        managed.add(key);
                    }
                } else if (rawVersion != null || rawVersionRef != null) {
                    version = optionalVersionOrRef(dependencyTable, section + "." + key, versionAliases)
                            .orElseThrow();
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
