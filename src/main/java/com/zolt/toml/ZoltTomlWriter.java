package com.zolt.toml;

import com.zolt.project.BuildSettings;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencySection;
import com.zolt.project.ProjectConfig;
import com.zolt.project.VersionPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ZoltTomlWriter {
    public ProjectConfig defaultApplicationConfig(String name, String group, String mainClass) {
        return new ProjectConfig(
                ProjectSectionCodec.defaultApplicationProject(name, group, mainClass),
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
        ProjectSectionCodec.write(toml, config.project());
        RepositorySectionCodec.writeRepositories(toml, config.repositorySettings());
        RepositorySectionCodec.writeRepositoryCredentials(toml, config.repositoryCredentials());
        VersionAliasSectionCodec.write(toml, config.versionAliases());
        PlatformSectionCodec.write(toml, config.platforms(), config.dependencyMetadata());
        DependencyPolicySectionCodec.write(toml, config.dependencyPolicy());
        DependencySectionCodec.write(toml, config);
        BuildSectionCodec.writeTestSources(toml, config.build());
        BuildSectionCodec.writeTestRuntime(toml, config.build().testRuntime());
        BuildSectionCodec.writeBuild(toml, config.build());
        BuildSectionCodec.writeBuildMetadata(toml, config.build().metadata());
        BuildSectionCodec.writeResources(toml, config.build());
        GeneratedSectionCodec.write(toml, config.build());
        CompilerSectionCodec.write(toml, config.compilerSettings());
        PackageSectionCodec.write(toml, config.packageSettings());
        FrameworkSectionCodec.write(toml, config.frameworkSettings());
        NativeSectionCodec.write(toml, config.nativeSettings());
        return toml.toString();
    }

    public ProjectConfig addDependency(
            ProjectConfig config,
            DependencySection section,
            String coordinate,
            String version) {
        validateVersion(VersionPolicy.Context.EXTERNAL_DEPENDENCY, coordinate, version);
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

    public ProjectConfig addVersionRefDependency(
            ProjectConfig config,
            DependencySection section,
            String coordinate,
            String versionRef,
            String version) {
        validateVersion(VersionPolicy.Context.EXTERNAL_DEPENDENCY, coordinate, version);
        ProjectConfig updated = addDependency(config, section, coordinate, version);
        String sectionName = sectionName(section);
        DependencyMetadata existing = config.dependencyMetadata()
                .get(DependencyMetadata.key(sectionName, coordinate));
        DependencyMetadata metadata = new DependencyMetadata(
                sectionName,
                coordinate,
                version,
                versionRef,
                false,
                null,
                existing != null && existing.optional(),
                existing != null && existing.publishOnly(),
                existing == null ? List.of() : existing.exclusions());
        Map<String, DependencyMetadata> dependencyMetadata = new LinkedHashMap<>(updated.dependencyMetadata());
        dependencyMetadata.put(DependencyMetadata.key(sectionName, coordinate), metadata);
        return updated.withDependencyMetadata(dependencyMetadata);
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
        validateVersion(VersionPolicy.Context.PLATFORM, coordinate, version);
        ProjectConfig updated = copy(
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
        return updated.withDependencyMetadata(removePlatformMetadata(updated.dependencyMetadata(), coordinate));
    }

    public ProjectConfig addVersionRefPlatform(
            ProjectConfig config,
            String coordinate,
            String versionRef,
            String version) {
        validateVersion(VersionPolicy.Context.PLATFORM, coordinate, version);
        ProjectConfig updated = copy(
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
        Map<String, DependencyMetadata> metadata = new LinkedHashMap<>(updated.dependencyMetadata());
        metadata.put(
                DependencyMetadata.key("platforms", coordinate),
                new DependencyMetadata(
                        "platforms",
                        coordinate,
                        version,
                        versionRef,
                        false,
                        null,
                        false,
                        false,
                        List.of()));
        return updated.withDependencyMetadata(metadata);
    }

    public ProjectConfig removePlatform(ProjectConfig config, String coordinate) {
        ProjectConfig updated = copy(
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
        return updated.withDependencyMetadata(removePlatformMetadata(updated.dependencyMetadata(), coordinate));
    }

    private static Map<String, DependencyMetadata> removePlatformMetadata(
            Map<String, DependencyMetadata> dependencyMetadata,
            String coordinate) {
        if (!dependencyMetadata.containsKey(DependencyMetadata.key("platforms", coordinate))) {
            return dependencyMetadata;
        }
        Map<String, DependencyMetadata> metadata = new LinkedHashMap<>(dependencyMetadata);
        metadata.remove(DependencyMetadata.key("platforms", coordinate));
        return metadata;
    }

    private static void validateVersion(
            VersionPolicy.Context context,
            String coordinate,
            String version) {
        VersionPolicy.violation(context, version).ifPresent(violation -> {
            throw new IllegalArgumentException(
                    "Invalid "
                            + context.description()
                            + " `"
                            + version
                            + "` for "
                            + coordinate
                            + ". "
                            + violation.guidance());
        });
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
                        managedTestAnnotationProcessors));
    }

    private static Map<String, DependencyMetadata> retainedDependencyMetadata(
            Map<String, DependencyMetadata> metadata,
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
        Map<String, DependencyMetadata> retained = new LinkedHashMap<>();
        for (DependencyMetadata value : metadata.values()) {
            if (value.publishOnly() || containsDependency(
                    value.section(),
                    value.coordinate(),
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
                    managedTestAnnotationProcessors)) {
                retained.put(DependencyMetadata.key(value.section(), value.coordinate()), value);
            }
        }
        return retained;
    }

    private static boolean containsDependency(
            String section,
            String coordinate,
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
        return switch (section) {
            case "platforms" -> platforms.containsKey(coordinate);
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

    private static String sectionName(DependencySection section) {
        return switch (section) {
            case MAIN -> "dependencies";
            case API -> "api.dependencies";
            case RUNTIME -> "runtime.dependencies";
            case PROVIDED -> "provided.dependencies";
            case DEV -> "dev.dependencies";
            case TEST -> "test.dependencies";
            case PROCESSOR -> "annotationProcessors";
            case TEST_PROCESSOR -> "test.annotationProcessors";
        };
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
