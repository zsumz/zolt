package com.zolt.toml;

import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.NativeSettings;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.RepositoryCredentialSettings;
import com.zolt.project.RepositorySettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.tomlj.Toml;
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
        DependencyPolicySettings dependencyPolicy = DependencyPolicySectionCodec.parse(
                optionalTable(result, "dependencyPolicy"),
                optionalTable(result, "dependencyConstraints"),
                versionAliases);

        TomlTable testTable = optionalTable(result, "test");
        DependencySectionCodec.ParsedDependencies parsedDependencies =
                DependencySectionCodec.parse(result, dependencyMetadata, versionAliases);

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
                parsedDependencies.apiDependencies().versioned(),
                parsedDependencies.apiDependencies().managed(),
                parsedDependencies.apiDependencies().workspace(),
                parsedDependencies.implementationDependencies().versioned(),
                parsedDependencies.implementationDependencies().managed(),
                parsedDependencies.implementationDependencies().workspace(),
                parsedDependencies.runtimeDependencies().versioned(),
                parsedDependencies.runtimeDependencies().managed(),
                parsedDependencies.providedDependencies().versioned(),
                parsedDependencies.providedDependencies().managed(),
                parsedDependencies.devDependencies().versioned(),
                parsedDependencies.devDependencies().managed(),
                parsedDependencies.testDependencies().versioned(),
                parsedDependencies.testDependencies().managed(),
                parsedDependencies.testDependencies().workspace(),
                parsedDependencies.annotationProcessors().versioned(),
                parsedDependencies.annotationProcessors().managed(),
                parsedDependencies.testAnnotationProcessors().versioned(),
                parsedDependencies.testAnnotationProcessors().managed(),
                dependencyPolicy,
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                frameworkSettings,
                parsedDependencies.dependencyMetadata());
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

    private static TomlTable optionalTable(TomlParseResult result, String section) {
        return result.getTable(section);
    }

}
