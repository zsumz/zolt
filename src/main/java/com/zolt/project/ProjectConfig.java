package com.zolt.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record ProjectConfig(
        ProjectMetadata project,
        Map<String, String> repositories,
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
        Set<String> managedTestAnnotationProcessors,
        BuildSettings build,
        NativeSettings nativeSettings,
        CompilerSettings compilerSettings,
        PackageSettings packageSettings,
        FrameworkSettings frameworkSettings) {
    public static final String MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2";

    public ProjectConfig {
        repositories = orderedMap(repositories);
        platforms = orderedMap(platforms);
        apiDependencies = orderedMap(apiDependencies);
        managedApiDependencies = orderedSet(managedApiDependencies);
        workspaceApiDependencies = orderedMap(workspaceApiDependencies);
        dependencies = orderedMap(dependencies);
        managedDependencies = orderedSet(managedDependencies);
        workspaceDependencies = orderedMap(workspaceDependencies);
        runtimeDependencies = orderedMap(runtimeDependencies);
        managedRuntimeDependencies = orderedSet(managedRuntimeDependencies);
        providedDependencies = orderedMap(providedDependencies);
        managedProvidedDependencies = orderedSet(managedProvidedDependencies);
        devDependencies = orderedMap(devDependencies);
        managedDevDependencies = orderedSet(managedDevDependencies);
        testDependencies = orderedMap(testDependencies);
        managedTestDependencies = orderedSet(managedTestDependencies);
        workspaceTestDependencies = orderedMap(workspaceTestDependencies);
        annotationProcessors = orderedMap(annotationProcessors);
        managedAnnotationProcessors = orderedSet(managedAnnotationProcessors);
        testAnnotationProcessors = orderedMap(testAnnotationProcessors);
        managedTestAnnotationProcessors = orderedSet(managedTestAnnotationProcessors);
        nativeSettings = nativeSettings == null ? NativeSettings.defaults() : nativeSettings;
        compilerSettings = compilerSettings == null ? CompilerSettings.defaults() : compilerSettings;
        packageSettings = packageSettings == null ? PackageSettings.defaults() : packageSettings;
        frameworkSettings = frameworkSettings == null ? FrameworkSettings.defaults() : frameworkSettings;
    }

    public ProjectConfig(
            ProjectMetadata project,
            Map<String, String> repositories,
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
            Set<String> managedTestAnnotationProcessors,
            BuildSettings build,
            NativeSettings nativeSettings,
            CompilerSettings compilerSettings,
            PackageSettings packageSettings) {
        this(
                project,
                repositories,
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
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                FrameworkSettings.defaults());
    }

    public ProjectConfig(
            ProjectMetadata project,
            Map<String, String> repositories,
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
            Set<String> managedTestAnnotationProcessors,
            BuildSettings build,
            NativeSettings nativeSettings,
            CompilerSettings compilerSettings) {
        this(
                project,
                repositories,
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
                build,
                nativeSettings,
                compilerSettings,
                PackageSettings.defaults());
    }

    public ProjectConfig(
            ProjectMetadata project,
            Map<String, String> repositories,
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
            Map<String, String> testDependencies,
            Set<String> managedTestDependencies,
            Map<String, String> workspaceTestDependencies,
            Map<String, String> annotationProcessors,
            Set<String> managedAnnotationProcessors,
            Map<String, String> testAnnotationProcessors,
            Set<String> managedTestAnnotationProcessors,
            BuildSettings build,
            NativeSettings nativeSettings,
            CompilerSettings compilerSettings) {
        this(
                project,
                repositories,
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
                Map.of(),
                Set.of(),
                testDependencies,
                managedTestDependencies,
                workspaceTestDependencies,
                annotationProcessors,
                managedAnnotationProcessors,
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                build,
                nativeSettings,
                compilerSettings,
                PackageSettings.defaults());
    }

    public ProjectConfig(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> platforms,
            Map<String, String> apiDependencies,
            Set<String> managedApiDependencies,
            Map<String, String> workspaceApiDependencies,
            Map<String, String> dependencies,
            Set<String> managedDependencies,
            Map<String, String> workspaceDependencies,
            Map<String, String> testDependencies,
            Set<String> managedTestDependencies,
            Map<String, String> workspaceTestDependencies,
            Map<String, String> annotationProcessors,
            Set<String> managedAnnotationProcessors,
            Map<String, String> testAnnotationProcessors,
            Set<String> managedTestAnnotationProcessors,
            BuildSettings build,
            NativeSettings nativeSettings,
            CompilerSettings compilerSettings,
            PackageSettings packageSettings) {
        this(
                project,
                repositories,
                platforms,
                apiDependencies,
                managedApiDependencies,
                workspaceApiDependencies,
                dependencies,
                managedDependencies,
                workspaceDependencies,
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                testDependencies,
                managedTestDependencies,
                workspaceTestDependencies,
                annotationProcessors,
                managedAnnotationProcessors,
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                FrameworkSettings.defaults());
    }

    public ProjectConfig(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> platforms,
            Map<String, String> dependencies,
            Set<String> managedDependencies,
            Map<String, String> testDependencies,
            Set<String> managedTestDependencies,
            Map<String, String> annotationProcessors,
            Set<String> managedAnnotationProcessors,
            Map<String, String> testAnnotationProcessors,
            Set<String> managedTestAnnotationProcessors,
            BuildSettings build,
            NativeSettings nativeSettings,
            CompilerSettings compilerSettings) {
        this(
                project,
                repositories,
                platforms,
                Map.of(),
                Set.of(),
                Map.of(),
                dependencies,
                managedDependencies,
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                testDependencies,
                managedTestDependencies,
                Map.of(),
                annotationProcessors,
                managedAnnotationProcessors,
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                build,
                nativeSettings,
                compilerSettings);
    }

    public ProjectConfig(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> platforms,
            Map<String, String> dependencies,
            Set<String> managedDependencies,
            Map<String, String> testDependencies,
            Set<String> managedTestDependencies,
            Map<String, String> annotationProcessors,
            Set<String> managedAnnotationProcessors,
            Map<String, String> testAnnotationProcessors,
            Set<String> managedTestAnnotationProcessors,
            BuildSettings build,
            NativeSettings nativeSettings) {
        this(
                project,
                repositories,
                platforms,
                dependencies,
                managedDependencies,
                testDependencies,
                managedTestDependencies,
                annotationProcessors,
                managedAnnotationProcessors,
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                build,
                nativeSettings,
                CompilerSettings.defaults());
    }

    public ProjectConfig(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> platforms,
            Map<String, String> dependencies,
            Set<String> managedDependencies,
            Map<String, String> workspaceDependencies,
            Map<String, String> testDependencies,
            Set<String> managedTestDependencies,
            Map<String, String> workspaceTestDependencies,
            Map<String, String> annotationProcessors,
            Set<String> managedAnnotationProcessors,
            Map<String, String> testAnnotationProcessors,
            Set<String> managedTestAnnotationProcessors,
            BuildSettings build,
            NativeSettings nativeSettings) {
        this(
                project,
                repositories,
                platforms,
                Map.of(),
                Set.of(),
                Map.of(),
                dependencies,
                managedDependencies,
                workspaceDependencies,
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                testDependencies,
                managedTestDependencies,
                workspaceTestDependencies,
                annotationProcessors,
                managedAnnotationProcessors,
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                build,
                nativeSettings,
                CompilerSettings.defaults());
    }

    public ProjectConfig(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> platforms,
            Map<String, String> dependencies,
            Set<String> managedDependencies,
            Map<String, String> testDependencies,
            Set<String> managedTestDependencies,
            BuildSettings build,
            NativeSettings nativeSettings) {
        this(
                project,
                repositories,
                platforms,
                Map.of(),
                Set.of(),
                Map.of(),
                dependencies,
                managedDependencies,
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                testDependencies,
                managedTestDependencies,
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                build,
                nativeSettings,
                CompilerSettings.defaults());
    }

    public ProjectConfig(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> dependencies,
            Map<String, String> testDependencies,
            BuildSettings build,
            NativeSettings nativeSettings) {
        this(
                project,
                repositories,
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                dependencies,
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                testDependencies,
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                build,
                nativeSettings,
                CompilerSettings.defaults());
    }

    public ProjectConfig(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> dependencies,
            Map<String, String> testDependencies,
            BuildSettings build) {
        this(project, repositories, dependencies, testDependencies, build, NativeSettings.defaults());
    }

    public static Map<String, String> defaultRepositories() {
        return Map.of("central", MAVEN_CENTRAL);
    }

    private static Map<String, String> orderedMap(Map<String, String> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Set<String> orderedSet(Set<String> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    public ProjectConfig withBuildSettings(BuildSettings build) {
        return new ProjectConfig(
                project,
                repositories,
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
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                frameworkSettings);
    }

    public ProjectConfig withPackageSettings(PackageSettings packageSettings) {
        return new ProjectConfig(
                project,
                repositories,
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
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                frameworkSettings);
    }

    public ProjectConfig withFrameworkSettings(FrameworkSettings frameworkSettings) {
        return new ProjectConfig(
                project,
                repositories,
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
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                frameworkSettings);
    }
}
