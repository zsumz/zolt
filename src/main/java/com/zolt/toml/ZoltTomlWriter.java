package com.zolt.toml;

import com.zolt.project.BuildSettings;
import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.DependencySection;
import com.zolt.project.NativeSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
        writeStringMap(toml, "repositories", config.repositories());
        writeStringMap(toml, "platforms", config.platforms());
        writeOptionalDependencies(
                toml,
                "api.dependencies",
                config.apiDependencies(),
                config.managedApiDependencies(),
                config.workspaceApiDependencies());
        writeDependencies(
                toml,
                "dependencies",
                config.dependencies(),
                config.managedDependencies(),
                config.workspaceDependencies());
        writeOptionalDependencies(
                toml,
                "runtime.dependencies",
                config.runtimeDependencies(),
                config.managedRuntimeDependencies());
        writeOptionalDependencies(
                toml,
                "provided.dependencies",
                config.providedDependencies(),
                config.managedProvidedDependencies());
        writeOptionalDependencies(
                toml,
                "dev.dependencies",
                config.devDependencies(),
                config.managedDevDependencies());
        writeDependencies(
                toml,
                "test.dependencies",
                config.testDependencies(),
                config.managedTestDependencies(),
                config.workspaceTestDependencies());
        writeOptionalDependencies(
                toml,
                "annotationProcessors",
                config.annotationProcessors(),
                config.managedAnnotationProcessors());
        writeOptionalDependencies(
                toml,
                "test.annotationProcessors",
                config.testAnnotationProcessors(),
                config.managedTestAnnotationProcessors());
        writeTestSources(toml, config.build());
        writeBuild(toml, config.build());
        writeBuildMetadata(toml, config.build().metadata());
        writeResources(toml, config.build());
        writeCompiler(toml, config.compilerSettings());
        writePackage(toml, config.packageSettings());
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
                config.build(),
                config.nativeSettings(),
                config.compilerSettings(),
                config.packageSettings());
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
        if (build.testSources().equals(List.of(build.test()))) {
            return;
        }
        toml.append("[test.sources]\n");
        writeStringArray(toml, "java", build.testSources());
        toml.append('\n');
    }

    private static void writeResources(StringBuilder toml, BuildSettings build) {
        BuildSettings defaults = BuildSettings.defaults();
        if (build.resourceRoots().equals(defaults.resourceRoots())
                && build.testResourceRoots().equals(defaults.testResourceRoots())) {
            return;
        }
        toml.append("\n[resources]\n");
        writeStringArray(toml, "main", build.resourceRoots());
        writeStringArray(toml, "test", build.testResourceRoots());
    }

    private static void writeCompiler(StringBuilder toml, CompilerSettings compilerSettings) {
        CompilerSettings defaults = CompilerSettings.defaults();
        if (compilerSettings == null || compilerSettings.equals(defaults)) {
            return;
        }
        toml.append("\n[compiler]\n");
        writeAssignment(toml, "generatedSources", compilerSettings.generatedSources());
        writeAssignment(toml, "generatedTestSources", compilerSettings.generatedTestSources());
    }

    private static void writePackage(StringBuilder toml, PackageSettings packageSettings) {
        if (packageSettings == null || packageSettings.mode() == PackageMode.THIN) {
            return;
        }
        toml.append("\n[package]\n");
        writeAssignment(toml, "mode", packageSettings.mode().configValue());
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

    private static void writeDependencies(
            StringBuilder toml,
            String section,
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace) {
        toml.append('[').append(section).append("]\n");
        for (String coordinate : sortedCoordinates(versioned, managed, workspace)) {
            toml.append(quote(coordinate)).append(" = ");
            String workspacePath = workspace.get(coordinate);
            if (workspacePath != null) {
                toml.append("{ workspace = ").append(quote(workspacePath)).append(" }");
            } else {
                String version = versioned.get(coordinate);
                if (version == null) {
                    toml.append("{}");
                } else {
                    toml.append(quote(version));
                }
            }
            toml.append('\n');
        }
        toml.append('\n');
    }

    private static void writeOptionalDependencies(
            StringBuilder toml,
            String section,
            Map<String, String> versioned,
            Set<String> managed) {
        if (versioned.isEmpty() && managed.isEmpty()) {
            return;
        }
        writeDependencies(toml, section, versioned, managed, Map.of());
    }

    private static void writeOptionalDependencies(
            StringBuilder toml,
            String section,
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace) {
        if (versioned.isEmpty() && managed.isEmpty() && workspace.isEmpty()) {
            return;
        }
        writeDependencies(toml, section, versioned, managed, workspace);
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

    private static Set<String> sortedCoordinates(
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace) {
        TreeSet<String> coordinates = new TreeSet<>();
        coordinates.addAll(versioned.keySet());
        coordinates.addAll(managed);
        coordinates.addAll(workspace.keySet());
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
