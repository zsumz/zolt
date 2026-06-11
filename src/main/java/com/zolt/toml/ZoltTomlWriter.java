package com.zolt.toml;

import com.zolt.project.BuildSettings;
import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyExclusionSpec;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencyPolicyExclusion;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.project.DependencySection;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.NativeSettings;
import com.zolt.project.OpenApiGenerationSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.PublicationMetadata;
import com.zolt.project.QuarkusSettings;
import com.zolt.project.RepositoryCredentialSettings;
import com.zolt.project.RepositorySettings;
import com.zolt.project.ResourceFilteringSettings;
import com.zolt.project.ResourceMissingTokenPolicy;
import com.zolt.project.ResourceTokenSettings;
import com.zolt.project.TestRuntimeSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class ZoltTomlWriter {
    public ProjectConfig defaultApplicationConfig(String name, String group, String mainClass) {
        return new ProjectConfig(
                new ProjectMetadata(name, "0.1.0", group, "21", Optional.ofNullable(blankToNull(mainClass))),
                ProjectConfig.defaultRepositories(),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }

    public void write(Path path, ProjectConfig config) {
        try {
            Files.writeString(path, write(config));
        } catch (IOException exception) {
            throw new ZoltConfigException(
                    "Could not write zolt.toml at " + path + ". Check that the directory exists and is writable.");
        }
    }

    public String write(ProjectConfig config) {
        StringBuilder toml = new StringBuilder();
        writeProject(toml, config.project());
        writeRepositories(toml, config.repositorySettings());
        writeRepositoryCredentials(toml, config.repositoryCredentials());
        if (!config.versionAliases().isEmpty()) {
            writeStringMap(toml, "versions", config.versionAliases());
        }
        writeStringMap(toml, "platforms", config.platforms());
        writeDependencyPolicy(toml, config.dependencyPolicy());
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
        writeTestSources(toml, config.build());
        writeTestRuntime(toml, config.build().testRuntime());
        writeBuild(toml, config.build());
        writeBuildMetadata(toml, config.build().metadata());
        writeResources(toml, config.build());
        writeGeneratedSources(toml, config.build());
        writeCompiler(toml, config.compilerSettings());
        writePackage(toml, config.packageSettings());
        writeFramework(toml, config.frameworkSettings());
        writeNative(toml, config.nativeSettings());
        return toml.toString();
    }

    public ProjectConfig addDependency(
            ProjectConfig config,
            DependencySection section,
            String coordinate,
            String version) {
        return switch (section) {
            case API -> copy(
                    config,
                    config.platforms(),
                    put(config.apiDependencies(), coordinate, version),
                    remove(config.managedApiDependencies(), coordinate),
                    remove(config.workspaceApiDependencies(), coordinate),
                    remove(config.dependencies(), coordinate),
                    remove(config.managedDependencies(), coordinate),
                    remove(config.workspaceDependencies(), coordinate),
                    remove(config.runtimeDependencies(), coordinate),
                    remove(config.managedRuntimeDependencies(), coordinate),
                    remove(config.providedDependencies(), coordinate),
                    remove(config.managedProvidedDependencies(), coordinate),
                    remove(config.devDependencies(), coordinate),
                    remove(config.managedDevDependencies(), coordinate),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case MAIN -> copy(
                    config,
                    config.platforms(),
                    remove(config.apiDependencies(), coordinate),
                    remove(config.managedApiDependencies(), coordinate),
                    remove(config.workspaceApiDependencies(), coordinate),
                    put(config.dependencies(), coordinate, version),
                    remove(config.managedDependencies(), coordinate),
                    remove(config.workspaceDependencies(), coordinate),
                    remove(config.runtimeDependencies(), coordinate),
                    remove(config.managedRuntimeDependencies(), coordinate),
                    remove(config.providedDependencies(), coordinate),
                    remove(config.managedProvidedDependencies(), coordinate),
                    remove(config.devDependencies(), coordinate),
                    remove(config.managedDevDependencies(), coordinate),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case RUNTIME -> copy(
                    config,
                    config.platforms(),
                    remove(config.apiDependencies(), coordinate),
                    remove(config.managedApiDependencies(), coordinate),
                    remove(config.workspaceApiDependencies(), coordinate),
                    remove(config.dependencies(), coordinate),
                    remove(config.managedDependencies(), coordinate),
                    remove(config.workspaceDependencies(), coordinate),
                    put(config.runtimeDependencies(), coordinate, version),
                    remove(config.managedRuntimeDependencies(), coordinate),
                    remove(config.providedDependencies(), coordinate),
                    remove(config.managedProvidedDependencies(), coordinate),
                    remove(config.devDependencies(), coordinate),
                    remove(config.managedDevDependencies(), coordinate),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case PROVIDED -> copy(
                    config,
                    config.platforms(),
                    remove(config.apiDependencies(), coordinate),
                    remove(config.managedApiDependencies(), coordinate),
                    remove(config.workspaceApiDependencies(), coordinate),
                    remove(config.dependencies(), coordinate),
                    remove(config.managedDependencies(), coordinate),
                    remove(config.workspaceDependencies(), coordinate),
                    remove(config.runtimeDependencies(), coordinate),
                    remove(config.managedRuntimeDependencies(), coordinate),
                    put(config.providedDependencies(), coordinate, version),
                    remove(config.managedProvidedDependencies(), coordinate),
                    remove(config.devDependencies(), coordinate),
                    remove(config.managedDevDependencies(), coordinate),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case DEV -> copy(
                    config,
                    config.platforms(),
                    remove(config.apiDependencies(), coordinate),
                    remove(config.managedApiDependencies(), coordinate),
                    remove(config.workspaceApiDependencies(), coordinate),
                    remove(config.dependencies(), coordinate),
                    remove(config.managedDependencies(), coordinate),
                    remove(config.workspaceDependencies(), coordinate),
                    remove(config.runtimeDependencies(), coordinate),
                    remove(config.managedRuntimeDependencies(), coordinate),
                    remove(config.providedDependencies(), coordinate),
                    remove(config.managedProvidedDependencies(), coordinate),
                    put(config.devDependencies(), coordinate, version),
                    remove(config.managedDevDependencies(), coordinate),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case TEST -> copy(
                    config,
                    config.platforms(),
                    config.apiDependencies(),
                    config.managedApiDependencies(),
                    config.workspaceApiDependencies(),
                    config.dependencies(),
                    config.managedDependencies(),
                    config.workspaceDependencies(),
                    config.runtimeDependencies(),
                    config.managedRuntimeDependencies(),
                    config.providedDependencies(),
                    config.managedProvidedDependencies(),
                    config.devDependencies(),
                    config.managedDevDependencies(),
                    put(config.testDependencies(), coordinate, version),
                    remove(config.managedTestDependencies(), coordinate),
                    remove(config.workspaceTestDependencies(), coordinate),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case PROCESSOR -> copy(
                    config,
                    config.platforms(),
                    config.apiDependencies(),
                    config.managedApiDependencies(),
                    config.workspaceApiDependencies(),
                    config.dependencies(),
                    config.managedDependencies(),
                    config.workspaceDependencies(),
                    config.runtimeDependencies(),
                    config.managedRuntimeDependencies(),
                    config.providedDependencies(),
                    config.managedProvidedDependencies(),
                    config.devDependencies(),
                    config.managedDevDependencies(),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    put(config.annotationProcessors(), coordinate, version),
                    remove(config.managedAnnotationProcessors(), coordinate),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case TEST_PROCESSOR -> copy(
                    config,
                    config.platforms(),
                    config.apiDependencies(),
                    config.managedApiDependencies(),
                    config.workspaceApiDependencies(),
                    config.dependencies(),
                    config.managedDependencies(),
                    config.workspaceDependencies(),
                    config.runtimeDependencies(),
                    config.managedRuntimeDependencies(),
                    config.providedDependencies(),
                    config.managedProvidedDependencies(),
                    config.devDependencies(),
                    config.managedDevDependencies(),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    put(config.testAnnotationProcessors(), coordinate, version),
                    remove(config.managedTestAnnotationProcessors(), coordinate));
        };
    }

    public ProjectConfig addManagedDependency(ProjectConfig config, DependencySection section, String coordinate) {
        return switch (section) {
            case API -> copy(
                    config,
                    config.platforms(),
                    remove(config.apiDependencies(), coordinate),
                    add(config.managedApiDependencies(), coordinate),
                    remove(config.workspaceApiDependencies(), coordinate),
                    remove(config.dependencies(), coordinate),
                    remove(config.managedDependencies(), coordinate),
                    remove(config.workspaceDependencies(), coordinate),
                    remove(config.runtimeDependencies(), coordinate),
                    remove(config.managedRuntimeDependencies(), coordinate),
                    remove(config.providedDependencies(), coordinate),
                    remove(config.managedProvidedDependencies(), coordinate),
                    remove(config.devDependencies(), coordinate),
                    remove(config.managedDevDependencies(), coordinate),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case MAIN -> copy(
                    config,
                    config.platforms(),
                    remove(config.apiDependencies(), coordinate),
                    remove(config.managedApiDependencies(), coordinate),
                    remove(config.workspaceApiDependencies(), coordinate),
                    remove(config.dependencies(), coordinate),
                    add(config.managedDependencies(), coordinate),
                    remove(config.workspaceDependencies(), coordinate),
                    remove(config.runtimeDependencies(), coordinate),
                    remove(config.managedRuntimeDependencies(), coordinate),
                    remove(config.providedDependencies(), coordinate),
                    remove(config.managedProvidedDependencies(), coordinate),
                    remove(config.devDependencies(), coordinate),
                    remove(config.managedDevDependencies(), coordinate),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case RUNTIME -> copy(
                    config,
                    config.platforms(),
                    remove(config.apiDependencies(), coordinate),
                    remove(config.managedApiDependencies(), coordinate),
                    remove(config.workspaceApiDependencies(), coordinate),
                    remove(config.dependencies(), coordinate),
                    remove(config.managedDependencies(), coordinate),
                    remove(config.workspaceDependencies(), coordinate),
                    remove(config.runtimeDependencies(), coordinate),
                    add(config.managedRuntimeDependencies(), coordinate),
                    remove(config.providedDependencies(), coordinate),
                    remove(config.managedProvidedDependencies(), coordinate),
                    remove(config.devDependencies(), coordinate),
                    remove(config.managedDevDependencies(), coordinate),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case PROVIDED -> copy(
                    config,
                    config.platforms(),
                    remove(config.apiDependencies(), coordinate),
                    remove(config.managedApiDependencies(), coordinate),
                    remove(config.workspaceApiDependencies(), coordinate),
                    remove(config.dependencies(), coordinate),
                    remove(config.managedDependencies(), coordinate),
                    remove(config.workspaceDependencies(), coordinate),
                    remove(config.runtimeDependencies(), coordinate),
                    remove(config.managedRuntimeDependencies(), coordinate),
                    remove(config.providedDependencies(), coordinate),
                    add(config.managedProvidedDependencies(), coordinate),
                    remove(config.devDependencies(), coordinate),
                    remove(config.managedDevDependencies(), coordinate),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case DEV -> copy(
                    config,
                    config.platforms(),
                    remove(config.apiDependencies(), coordinate),
                    remove(config.managedApiDependencies(), coordinate),
                    remove(config.workspaceApiDependencies(), coordinate),
                    remove(config.dependencies(), coordinate),
                    remove(config.managedDependencies(), coordinate),
                    remove(config.workspaceDependencies(), coordinate),
                    remove(config.runtimeDependencies(), coordinate),
                    remove(config.managedRuntimeDependencies(), coordinate),
                    remove(config.providedDependencies(), coordinate),
                    remove(config.managedProvidedDependencies(), coordinate),
                    remove(config.devDependencies(), coordinate),
                    add(config.managedDevDependencies(), coordinate),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case TEST -> copy(
                    config,
                    config.platforms(),
                    config.apiDependencies(),
                    config.managedApiDependencies(),
                    config.workspaceApiDependencies(),
                    config.dependencies(),
                    config.managedDependencies(),
                    config.workspaceDependencies(),
                    config.runtimeDependencies(),
                    config.managedRuntimeDependencies(),
                    config.providedDependencies(),
                    config.managedProvidedDependencies(),
                    config.devDependencies(),
                    config.managedDevDependencies(),
                    remove(config.testDependencies(), coordinate),
                    add(config.managedTestDependencies(), coordinate),
                    remove(config.workspaceTestDependencies(), coordinate),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case PROCESSOR -> copy(
                    config,
                    config.platforms(),
                    config.apiDependencies(),
                    config.managedApiDependencies(),
                    config.workspaceApiDependencies(),
                    config.dependencies(),
                    config.managedDependencies(),
                    config.workspaceDependencies(),
                    config.runtimeDependencies(),
                    config.managedRuntimeDependencies(),
                    config.providedDependencies(),
                    config.managedProvidedDependencies(),
                    config.devDependencies(),
                    config.managedDevDependencies(),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    remove(config.annotationProcessors(), coordinate),
                    add(config.managedAnnotationProcessors(), coordinate),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case TEST_PROCESSOR -> copy(
                    config,
                    config.platforms(),
                    config.apiDependencies(),
                    config.managedApiDependencies(),
                    config.workspaceApiDependencies(),
                    config.dependencies(),
                    config.managedDependencies(),
                    config.workspaceDependencies(),
                    config.runtimeDependencies(),
                    config.managedRuntimeDependencies(),
                    config.providedDependencies(),
                    config.managedProvidedDependencies(),
                    config.devDependencies(),
                    config.managedDevDependencies(),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    remove(config.testAnnotationProcessors(), coordinate),
                    add(config.managedTestAnnotationProcessors(), coordinate));
        };
    }

    public ProjectConfig removeDependency(ProjectConfig config, DependencySection section, String coordinate) {
        return switch (section) {
            case API -> copy(
                    config,
                    config.platforms(),
                    remove(config.apiDependencies(), coordinate),
                    remove(config.managedApiDependencies(), coordinate),
                    remove(config.workspaceApiDependencies(), coordinate),
                    config.dependencies(),
                    config.managedDependencies(),
                    config.workspaceDependencies(),
                    config.runtimeDependencies(),
                    config.managedRuntimeDependencies(),
                    config.providedDependencies(),
                    config.managedProvidedDependencies(),
                    config.devDependencies(),
                    config.managedDevDependencies(),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case MAIN -> copy(
                    config,
                    config.platforms(),
                    config.apiDependencies(),
                    config.managedApiDependencies(),
                    config.workspaceApiDependencies(),
                    remove(config.dependencies(), coordinate),
                    remove(config.managedDependencies(), coordinate),
                    remove(config.workspaceDependencies(), coordinate),
                    config.runtimeDependencies(),
                    config.managedRuntimeDependencies(),
                    config.providedDependencies(),
                    config.managedProvidedDependencies(),
                    config.devDependencies(),
                    config.managedDevDependencies(),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case RUNTIME -> copy(
                    config,
                    config.platforms(),
                    config.apiDependencies(),
                    config.managedApiDependencies(),
                    config.workspaceApiDependencies(),
                    config.dependencies(),
                    config.managedDependencies(),
                    config.workspaceDependencies(),
                    remove(config.runtimeDependencies(), coordinate),
                    remove(config.managedRuntimeDependencies(), coordinate),
                    config.providedDependencies(),
                    config.managedProvidedDependencies(),
                    config.devDependencies(),
                    config.managedDevDependencies(),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case PROVIDED -> copy(
                    config,
                    config.platforms(),
                    config.apiDependencies(),
                    config.managedApiDependencies(),
                    config.workspaceApiDependencies(),
                    config.dependencies(),
                    config.managedDependencies(),
                    config.workspaceDependencies(),
                    config.runtimeDependencies(),
                    config.managedRuntimeDependencies(),
                    remove(config.providedDependencies(), coordinate),
                    remove(config.managedProvidedDependencies(), coordinate),
                    config.devDependencies(),
                    config.managedDevDependencies(),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case DEV -> copy(
                    config,
                    config.platforms(),
                    config.apiDependencies(),
                    config.managedApiDependencies(),
                    config.workspaceApiDependencies(),
                    config.dependencies(),
                    config.managedDependencies(),
                    config.workspaceDependencies(),
                    config.runtimeDependencies(),
                    config.managedRuntimeDependencies(),
                    config.providedDependencies(),
                    config.managedProvidedDependencies(),
                    remove(config.devDependencies(), coordinate),
                    remove(config.managedDevDependencies(), coordinate),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case TEST -> copy(
                    config,
                    config.platforms(),
                    config.apiDependencies(),
                    config.managedApiDependencies(),
                    config.workspaceApiDependencies(),
                    config.dependencies(),
                    config.managedDependencies(),
                    config.workspaceDependencies(),
                    config.runtimeDependencies(),
                    config.managedRuntimeDependencies(),
                    config.providedDependencies(),
                    config.managedProvidedDependencies(),
                    config.devDependencies(),
                    config.managedDevDependencies(),
                    remove(config.testDependencies(), coordinate),
                    remove(config.managedTestDependencies(), coordinate),
                    remove(config.workspaceTestDependencies(), coordinate),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case PROCESSOR -> copy(
                    config,
                    config.platforms(),
                    config.apiDependencies(),
                    config.managedApiDependencies(),
                    config.workspaceApiDependencies(),
                    config.dependencies(),
                    config.managedDependencies(),
                    config.workspaceDependencies(),
                    config.runtimeDependencies(),
                    config.managedRuntimeDependencies(),
                    config.providedDependencies(),
                    config.managedProvidedDependencies(),
                    config.devDependencies(),
                    config.managedDevDependencies(),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    remove(config.annotationProcessors(), coordinate),
                    remove(config.managedAnnotationProcessors(), coordinate),
                    config.testAnnotationProcessors(),
                    config.managedTestAnnotationProcessors());
            case TEST_PROCESSOR -> copy(
                    config,
                    config.platforms(),
                    config.apiDependencies(),
                    config.managedApiDependencies(),
                    config.workspaceApiDependencies(),
                    config.dependencies(),
                    config.managedDependencies(),
                    config.workspaceDependencies(),
                    config.runtimeDependencies(),
                    config.managedRuntimeDependencies(),
                    config.providedDependencies(),
                    config.managedProvidedDependencies(),
                    config.devDependencies(),
                    config.managedDevDependencies(),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.workspaceTestDependencies(),
                    config.annotationProcessors(),
                    config.managedAnnotationProcessors(),
                    remove(config.testAnnotationProcessors(), coordinate),
                    remove(config.managedTestAnnotationProcessors(), coordinate));
        };
    }

    public ProjectConfig addPlatform(ProjectConfig config, String coordinate, String version) {
        return copy(
                config,
                put(config.platforms(), coordinate, version),
                config.apiDependencies(),
                config.managedApiDependencies(),
                config.workspaceApiDependencies(),
                config.dependencies(),
                config.managedDependencies(),
                config.workspaceDependencies(),
                config.runtimeDependencies(),
                config.managedRuntimeDependencies(),
                config.providedDependencies(),
                config.managedProvidedDependencies(),
                config.devDependencies(),
                config.managedDevDependencies(),
                config.testDependencies(),
                config.managedTestDependencies(),
                config.workspaceTestDependencies(),
                config.annotationProcessors(),
                config.managedAnnotationProcessors(),
                config.testAnnotationProcessors(),
                config.managedTestAnnotationProcessors());
    }

    public ProjectConfig removePlatform(ProjectConfig config, String coordinate) {
        return copy(
                config,
                remove(config.platforms(), coordinate),
                config.apiDependencies(),
                config.managedApiDependencies(),
                config.workspaceApiDependencies(),
                config.dependencies(),
                config.managedDependencies(),
                config.workspaceDependencies(),
                config.runtimeDependencies(),
                config.managedRuntimeDependencies(),
                config.providedDependencies(),
                config.managedProvidedDependencies(),
                config.devDependencies(),
                config.managedDevDependencies(),
                config.testDependencies(),
                config.managedTestDependencies(),
                config.workspaceTestDependencies(),
                config.annotationProcessors(),
                config.managedAnnotationProcessors(),
                config.testAnnotationProcessors(),
                config.managedTestAnnotationProcessors());
    }

    private static ProjectConfig copy(
            ProjectConfig config,
            Map<String, String> platforms,
            Map<String, String> apiDependencies,
            Set<String> managedApiDependencies,
            Map<String, String> workspaceApiDependencies,
            Map<String, String> dependencies,
            Set<String> managedDependencies,
            Map<String, String> workspaceDependencies,
            Map<String, String> runtimeDependencies,
            Set<String> managedRuntimeDependencies,
            Map<String, String> providedDependencies,
            Set<String> managedProvidedDependencies,
            Map<String, String> devDependencies,
            Set<String> managedDevDependencies,
            Map<String, String> testDependencies,
            Set<String> managedTestDependencies,
            Map<String, String> workspaceTestDependencies,
            Map<String, String> annotationProcessors,
            Set<String> managedAnnotationProcessors,
            Map<String, String> testAnnotationProcessors,
            Set<String> managedTestAnnotationProcessors) {
        return new ProjectConfig(
                config.project(),
                config.repositories(),
                config.repositorySettings(),
                config.repositoryCredentials(),
                config.versionAliases(),
                platforms,
                apiDependencies,
                managedApiDependencies,
                workspaceApiDependencies,
                dependencies,
                managedDependencies,
                workspaceDependencies,
                runtimeDependencies,
                managedRuntimeDependencies,
                providedDependencies,
                managedProvidedDependencies,
                devDependencies,
                managedDevDependencies,
                testDependencies,
                managedTestDependencies,
                workspaceTestDependencies,
                annotationProcessors,
                managedAnnotationProcessors,
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                config.dependencyPolicy(),
                config.build(),
                config.nativeSettings(),
                config.compilerSettings(),
                config.packageSettings(),
                config.frameworkSettings(),
                retainedDependencyMetadata(
                        config.dependencyMetadata(),
                        apiDependencies,
                        managedApiDependencies,
                        workspaceApiDependencies,
                        dependencies,
                        managedDependencies,
                        workspaceDependencies,
                        runtimeDependencies,
                        managedRuntimeDependencies,
                        providedDependencies,
                        managedProvidedDependencies,
                        devDependencies,
                        managedDevDependencies,
                        testDependencies,
                        managedTestDependencies,
                        workspaceTestDependencies,
                        annotationProcessors,
                        managedAnnotationProcessors,
                        testAnnotationProcessors,
                        managedTestAnnotationProcessors));
    }

    private static Map<String, DependencyMetadata> retainedDependencyMetadata(
            Map<String, DependencyMetadata> metadata,
            Map<String, String> apiDependencies,
            Set<String> managedApiDependencies,
            Map<String, String> workspaceApiDependencies,
            Map<String, String> dependencies,
            Set<String> managedDependencies,
            Map<String, String> workspaceDependencies,
            Map<String, String> runtimeDependencies,
            Set<String> managedRuntimeDependencies,
            Map<String, String> providedDependencies,
            Set<String> managedProvidedDependencies,
            Map<String, String> devDependencies,
            Set<String> managedDevDependencies,
            Map<String, String> testDependencies,
            Set<String> managedTestDependencies,
            Map<String, String> workspaceTestDependencies,
            Map<String, String> annotationProcessors,
            Set<String> managedAnnotationProcessors,
            Map<String, String> testAnnotationProcessors,
            Set<String> managedTestAnnotationProcessors) {
        Map<String, DependencyMetadata> retained = new LinkedHashMap<>();
        for (DependencyMetadata value : metadata.values()) {
            if (value.publishOnly() || containsDependency(
                    value.section(),
                    value.coordinate(),
                    apiDependencies,
                    managedApiDependencies,
                    workspaceApiDependencies,
                    dependencies,
                    managedDependencies,
                    workspaceDependencies,
                    runtimeDependencies,
                    managedRuntimeDependencies,
                    providedDependencies,
                    managedProvidedDependencies,
                    devDependencies,
                    managedDevDependencies,
                    testDependencies,
                    managedTestDependencies,
                    workspaceTestDependencies,
                    annotationProcessors,
                    managedAnnotationProcessors,
                    testAnnotationProcessors,
                    managedTestAnnotationProcessors)) {
                retained.put(DependencyMetadata.key(value.section(), value.coordinate()), value);
            }
        }
        return retained;
    }

    private static boolean containsDependency(
            String section,
            String coordinate,
            Map<String, String> apiDependencies,
            Set<String> managedApiDependencies,
            Map<String, String> workspaceApiDependencies,
            Map<String, String> dependencies,
            Set<String> managedDependencies,
            Map<String, String> workspaceDependencies,
            Map<String, String> runtimeDependencies,
            Set<String> managedRuntimeDependencies,
            Map<String, String> providedDependencies,
            Set<String> managedProvidedDependencies,
            Map<String, String> devDependencies,
            Set<String> managedDevDependencies,
            Map<String, String> testDependencies,
            Set<String> managedTestDependencies,
            Map<String, String> workspaceTestDependencies,
            Map<String, String> annotationProcessors,
            Set<String> managedAnnotationProcessors,
            Map<String, String> testAnnotationProcessors,
            Set<String> managedTestAnnotationProcessors) {
        return switch (section) {
            case "api.dependencies" -> contains(apiDependencies, managedApiDependencies, workspaceApiDependencies, coordinate);
            case "dependencies" -> contains(dependencies, managedDependencies, workspaceDependencies, coordinate);
            case "runtime.dependencies" -> contains(runtimeDependencies, managedRuntimeDependencies, Map.of(), coordinate);
            case "provided.dependencies" -> contains(providedDependencies, managedProvidedDependencies, Map.of(), coordinate);
            case "dev.dependencies" -> contains(devDependencies, managedDevDependencies, Map.of(), coordinate);
            case "test.dependencies" -> contains(testDependencies, managedTestDependencies, workspaceTestDependencies, coordinate);
            case "annotationProcessors" -> contains(annotationProcessors, managedAnnotationProcessors, Map.of(), coordinate);
            case "test.annotationProcessors" -> contains(testAnnotationProcessors, managedTestAnnotationProcessors, Map.of(), coordinate);
            default -> false;
        };
    }

    private static boolean contains(
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace,
            String coordinate) {
        return versioned.containsKey(coordinate)
                || managed.contains(coordinate)
                || workspace.containsKey(coordinate);
    }

    private static void writeProject(StringBuilder toml, ProjectMetadata project) {
        toml.append("[project]\n");
        writeAssignment(toml, "name", project.name());
        writeAssignment(toml, "version", project.version());
        writeAssignment(toml, "group", project.group());
        writeAssignment(toml, "java", project.java());
        project.main().ifPresent(mainClass -> writeAssignment(toml, "main", mainClass));
        toml.append('\n');
    }

    private static void writeBuild(StringBuilder toml, BuildSettings build) {
        toml.append("[build]\n");
        writeAssignment(toml, "source", build.source());
        writeAssignment(toml, "test", build.test());
        writeAssignment(toml, "output", build.output());
        writeAssignment(toml, "testOutput", build.testOutput());
    }

    private static void writeBuildMetadata(StringBuilder toml, BuildMetadataSettings metadata) {
        if (metadata == null || metadata.equals(BuildMetadataSettings.defaults())) {
            return;
        }
        toml.append("\n[build.metadata]\n");
        writeAssignment(toml, "buildInfo", metadata.buildInfo());
        writeAssignment(toml, "git", metadata.git());
        writeAssignment(toml, "reproducible", metadata.reproducible());
    }

    private static void writeTestSources(StringBuilder toml, BuildSettings build) {
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

    private static void writeTestRuntime(StringBuilder toml, TestRuntimeSettings runtime) {
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

    private static void writeResources(StringBuilder toml, BuildSettings build) {
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

    private static void writeGeneratedSources(StringBuilder toml, BuildSettings build) {
        writeOpenApiTool(toml, build);
        writeGeneratedSourceScope(toml, "generated.main", build.generatedMainSources());
        writeGeneratedSourceScope(toml, "generated.test", build.generatedTestSources());
    }

    private static void writeOpenApiTool(StringBuilder toml, BuildSettings build) {
        Optional<OpenApiGenerationSettings> settings = java.util.stream.Stream
                .concat(build.generatedMainSources().stream(), build.generatedTestSources().stream())
                .filter(step -> step.kind() == GeneratedSourceKind.OPENAPI)
                .map(GeneratedSourceStep::openApi)
                .filter(openApi -> openApi.toolCoordinate().isPresent() || openApi.toolVersion().isPresent())
                .findFirst();
        if (settings.isEmpty()) {
            return;
        }
        toml.append("\n[generated.openapiTool]\n");
        settings.orElseThrow().toolCoordinate().ifPresent(coordinate -> writeAssignment(toml, "coordinate", coordinate));
        settings.orElseThrow().toolVersion().ifPresent(version -> writeAssignment(toml, "version", version));
    }

    private static void writeGeneratedSourceScope(
            StringBuilder toml,
            String section,
            List<GeneratedSourceStep> steps) {
        if (steps.isEmpty()) {
            return;
        }
        for (GeneratedSourceStep step : steps.stream()
                .sorted(java.util.Comparator.comparing(GeneratedSourceStep::id))
                .toList()) {
            toml.append("\n[").append(section).append('.').append(step.id()).append("]\n");
            writeAssignment(toml, "kind", step.kind().configValue());
            writeAssignment(toml, "language", step.language());
            writeAssignment(toml, "output", step.output());
            if (step.kind() == GeneratedSourceKind.OPENAPI) {
                writeAssignment(toml, "input", step.inputs().getFirst());
                writeOpenApiSettings(toml, step.openApi());
            } else {
                writeStringArray(toml, "inputs", step.inputs());
            }
            if (!step.required()) {
                writeAssignment(toml, "required", false);
            }
            if (step.kind() == GeneratedSourceKind.OPENAPI && !step.clean()) {
                writeAssignment(toml, "clean", false);
            } else if (step.kind() != GeneratedSourceKind.OPENAPI && step.clean()) {
                writeAssignment(toml, "clean", true);
            }
        }
    }

    private static void writeOpenApiSettings(StringBuilder toml, OpenApiGenerationSettings settings) {
        settings.generator().ifPresent(value -> writeAssignment(toml, "generator", value));
        settings.library().ifPresent(value -> writeAssignment(toml, "library", value));
        settings.apiPackage().ifPresent(value -> writeAssignment(toml, "apiPackage", value));
        settings.modelPackage().ifPresent(value -> writeAssignment(toml, "modelPackage", value));
        settings.invokerPackage().ifPresent(value -> writeAssignment(toml, "invokerPackage", value));
        settings.config().ifPresent(value -> writeAssignment(toml, "config", value));
        settings.templateDir().ifPresent(value -> writeAssignment(toml, "templateDir", value));
        settings.validateSpec().ifPresent(value -> writeAssignment(toml, "validateSpec", value));
        if (!settings.options().isEmpty()) {
            writeInlineStringMap(toml, "options", settings.options());
        }
        if (!settings.additionalProperties().isEmpty()) {
            writeInlineStringMap(toml, "additionalProperties", settings.additionalProperties());
        }
        if (!settings.configOptions().isEmpty()) {
            writeInlineStringMap(toml, "configOptions", settings.configOptions());
        }
        if (!settings.globalProperties().isEmpty()) {
            writeInlineStringMap(toml, "globalProperties", settings.globalProperties());
        }
        if (!settings.typeMappings().isEmpty()) {
            writeInlineStringMap(toml, "typeMappings", settings.typeMappings());
        }
        if (!settings.importMappings().isEmpty()) {
            writeInlineStringMap(toml, "importMappings", settings.importMappings());
        }
    }

    private static void writeCompiler(StringBuilder toml, CompilerSettings compilerSettings) {
        CompilerSettings defaults = CompilerSettings.defaults();
        if (compilerSettings == null || compilerSettings.equals(defaults)) {
            return;
        }
        toml.append("\n[compiler]\n");
        writeAssignment(toml, "generatedSources", compilerSettings.generatedSources());
        writeAssignment(toml, "generatedTestSources", compilerSettings.generatedTestSources());
        if (!compilerSettings.release().isBlank()) {
            writeAssignment(toml, "release", compilerSettings.release());
        }
        if (!compilerSettings.encoding().isBlank()) {
            writeAssignment(toml, "encoding", compilerSettings.encoding());
        }
        if (!compilerSettings.args().isEmpty()) {
            writeStringArray(toml, "args", compilerSettings.args());
        }
        if (!compilerSettings.testArgs().isEmpty()) {
            writeStringArray(toml, "testArgs", compilerSettings.testArgs());
        }
    }

    private static void writePackage(StringBuilder toml, PackageSettings packageSettings) {
        if (packageSettings == null || packageSettings.equals(PackageSettings.defaults())) {
            return;
        }
        toml.append("\n[package]\n");
        if (packageSettings.mode() != PackageMode.THIN) {
            writeAssignment(toml, "mode", packageSettings.mode().configValue());
        }
        if (packageSettings.sources()) {
            writeAssignment(toml, "sources", true);
        }
        if (packageSettings.javadoc()) {
            writeAssignment(toml, "javadoc", true);
        }
        if (packageSettings.tests()) {
            writeAssignment(toml, "tests", true);
        }
        writePublicationMetadata(toml, packageSettings.metadata());
        writeManifestAttributes(toml, packageSettings.manifestAttributes());
    }

    private static void writePublicationMetadata(StringBuilder toml, PublicationMetadata metadata) {
        if (metadata == null || metadata.emptyMetadata()) {
            return;
        }
        toml.append("\n[package.metadata]\n");
        if (!metadata.name().isBlank()) {
            writeAssignment(toml, "name", metadata.name());
        }
        if (!metadata.description().isBlank()) {
            writeAssignment(toml, "description", metadata.description());
        }
        if (!metadata.url().isBlank()) {
            writeAssignment(toml, "url", metadata.url());
        }
        if (!metadata.license().isBlank()) {
            writeAssignment(toml, "license", metadata.license());
        }
        if (!metadata.developers().isEmpty()) {
            writeStringArray(toml, "developers", metadata.developers());
        }
        if (!metadata.scm().isBlank()) {
            writeAssignment(toml, "scm", metadata.scm());
        }
        if (!metadata.issues().isBlank()) {
            writeAssignment(toml, "issues", metadata.issues());
        }
    }

    private static void writeManifestAttributes(StringBuilder toml, Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        if (toml.length() > 1 && toml.charAt(toml.length() - 1) == '\n' && toml.charAt(toml.length() - 2) != '\n') {
            toml.append('\n');
        }
        writeStringMap(toml, "package.manifest", attributes);
    }

    private static void writeFramework(StringBuilder toml, FrameworkSettings frameworkSettings) {
        if (frameworkSettings == null || frameworkSettings.equals(FrameworkSettings.defaults())) {
            return;
        }
        QuarkusSettings quarkus = frameworkSettings.quarkus();
        if (!quarkus.equals(QuarkusSettings.defaults())) {
            toml.append("\n[framework.quarkus]\n");
            writeAssignment(toml, "enabled", quarkus.enabled());
            writeAssignment(toml, "package", quarkus.packageMode().configValue());
        }
    }

    private static void writeNative(StringBuilder toml, NativeSettings nativeSettings) {
        NativeSettings defaults = NativeSettings.defaults();
        if (nativeSettings == null
                || ((nativeSettings.imageName() == null || nativeSettings.imageName().isBlank())
                && nativeSettings.output().equals(defaults.output())
                && nativeSettings.args().isEmpty())) {
            return;
        }
        toml.append("\n[native]\n");
        if (nativeSettings.imageName() != null && !nativeSettings.imageName().isBlank()) {
            writeAssignment(toml, "imageName", nativeSettings.imageName());
        }
        writeAssignment(toml, "output", nativeSettings.output());
        writeStringArray(toml, "args", nativeSettings.args());
    }

    private static void writeStringMap(StringBuilder toml, String section, Map<String, String> values) {
        toml.append('[').append(section).append("]\n");
        for (Map.Entry<String, String> entry : sorted(values).entrySet()) {
            toml.append(quote(entry.getKey())).append(" = ").append(quote(entry.getValue())).append('\n');
        }
        toml.append('\n');
    }

    private static void writeInlineStringMap(StringBuilder toml, String key, Map<String, String> values) {
        toml.append(key).append(" = { ");
        int index = 0;
        for (Map.Entry<String, String> entry : sorted(values).entrySet()) {
            if (index > 0) {
                toml.append(", ");
            }
            toml.append(quote(entry.getKey())).append(" = ").append(quote(entry.getValue()));
            index++;
        }
        toml.append(" }\n");
    }

    private static void writeRepositories(StringBuilder toml, Map<String, RepositorySettings> repositories) {
        toml.append("[repositories]\n");
        for (Map.Entry<String, RepositorySettings> entry : sortedRepositorySettings(repositories).entrySet()) {
            RepositorySettings repository = entry.getValue();
            toml.append(quote(entry.getKey())).append(" = ");
            if (repository.credentials().isEmpty()) {
                toml.append(quote(repository.url())).append('\n');
                continue;
            }
            toml.append("{ url = ")
                    .append(quote(repository.url()))
                    .append(", credentials = ")
                    .append(quote(repository.credentials().orElseThrow()))
                    .append(" }\n");
        }
        toml.append('\n');
    }

    private static void writeRepositoryCredentials(
            StringBuilder toml,
            Map<String, RepositoryCredentialSettings> credentials) {
        for (RepositoryCredentialSettings credential : sortedRepositoryCredentials(credentials).values()) {
            toml.append("[repositoryCredentials.")
                    .append(quote(credential.id()))
                    .append("]\n");
            writeAssignment(toml, "usernameEnv", credential.usernameEnv());
            writeAssignment(toml, "passwordEnv", credential.passwordEnv());
            toml.append('\n');
        }
    }

    private static void writeDependencyPolicy(StringBuilder toml, DependencyPolicySettings policy) {
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
            for (DependencyConstraint constraint : sortedDependencyConstraints(policy.constraints()).values()) {
                toml.append(quote(constraint.coordinate())).append(" = { version = ")
                        .append(quote(constraint.version()))
                        .append(", kind = ")
                        .append(quote(constraint.kind().configValue()));
                constraint.reason().ifPresent(reason -> toml.append(", reason = ").append(quote(reason)));
                toml.append(" }\n");
            }
            toml.append('\n');
        }
    }

    private static String policyExclusion(DependencyPolicyExclusion exclusion) {
        List<String> parts = new ArrayList<>();
        parts.add("group = " + quote(exclusion.group()));
        parts.add("artifact = " + quote(exclusion.artifact()));
        exclusion.reason().ifPresent(reason -> parts.add("reason = " + quote(reason)));
        return "{ " + String.join(", ", parts) + " }";
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
        if (version != null) {
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

    private static void writeAssignment(StringBuilder toml, String key, String value) {
        toml.append(key).append(" = ").append(quote(value)).append('\n');
    }

    private static void writeAssignment(StringBuilder toml, String key, boolean value) {
        toml.append(key).append(" = ").append(value).append('\n');
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

    private static Map<String, String> put(Map<String, String> source, String coordinate, String version) {
        Map<String, String> updated = new LinkedHashMap<>(source);
        updated.put(coordinate, version);
        return updated;
    }

    private static Map<String, String> remove(Map<String, String> source, String coordinate) {
        Map<String, String> updated = new LinkedHashMap<>(source);
        updated.remove(coordinate);
        return updated;
    }

    private static Set<String> remove(Set<String> source, String coordinate) {
        TreeSet<String> updated = new TreeSet<>(source);
        updated.remove(coordinate);
        return updated;
    }

    private static Set<String> add(Set<String> source, String coordinate) {
        TreeSet<String> updated = new TreeSet<>(source);
        updated.add(coordinate);
        return updated;
    }

    private static Map<String, String> sorted(Map<String, String> values) {
        return new TreeMap<>(values);
    }

    private static Map<String, RepositorySettings> sortedRepositorySettings(Map<String, RepositorySettings> values) {
        return new TreeMap<>(values);
    }

    private static Map<String, RepositoryCredentialSettings> sortedRepositoryCredentials(
            Map<String, RepositoryCredentialSettings> values) {
        return new TreeMap<>(values);
    }

    private static Map<String, DependencyConstraint> sortedDependencyConstraints(
            Map<String, DependencyConstraint> values) {
        return new TreeMap<>(values);
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

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
