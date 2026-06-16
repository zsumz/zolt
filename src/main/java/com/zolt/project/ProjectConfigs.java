package com.zolt.project;

import java.util.Map;
import java.util.Set;

public final class ProjectConfigs {
    private ProjectConfigs() {
    }

    public static ProjectConfig withDependencySections(
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
        return new ProjectConfig(ProjectConfigConstruction.dependencySectionsWithPackage(
                project,
                repositories,
                platforms,
                apiDependencies,
                managedApiDependencies,
                workspaceApiDependencies,
                dependencies,
                managedDependencies,
                workspaceDependencies,
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
                packageSettings));
    }

    public static ProjectConfig withDependencySections(
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
        return new ProjectConfig(ProjectConfigConstruction.dependencySections(
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
                compilerSettings));
    }

    public static ProjectConfig withDependencySections(
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
        return withDependencySections(
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

    public static ProjectConfig withWorkspaceDependencySections(
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
        return new ProjectConfig(ProjectConfigConstruction.workspaceDependencySections(
                project,
                repositories,
                platforms,
                dependencies,
                managedDependencies,
                workspaceDependencies,
                testDependencies,
                managedTestDependencies,
                workspaceTestDependencies,
                annotationProcessors,
                managedAnnotationProcessors,
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                build,
                nativeSettings));
    }

    public static ProjectConfig withRuntimeDependencySections(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> platforms,
            Map<String, String> dependencies,
            Set<String> managedDependencies,
            Map<String, String> testDependencies,
            Set<String> managedTestDependencies,
            BuildSettings build,
            NativeSettings nativeSettings) {
        return new ProjectConfig(ProjectConfigConstruction.runtimeDependencySections(
                project,
                repositories,
                platforms,
                dependencies,
                managedDependencies,
                testDependencies,
                managedTestDependencies,
                build,
                nativeSettings));
    }

    public static ProjectConfig withDirectDependencies(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> dependencies,
            Map<String, String> testDependencies,
            BuildSettings build,
            NativeSettings nativeSettings) {
        return new ProjectConfig(ProjectConfigConstruction.directDependencies(
                project,
                repositories,
                dependencies,
                testDependencies,
                build,
                nativeSettings));
    }

    public static ProjectConfig withDirectDependencies(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> dependencies,
            Map<String, String> testDependencies,
            BuildSettings build) {
        return withDirectDependencies(
                project,
                repositories,
                dependencies,
                testDependencies,
                build,
                NativeSettings.defaults());
    }
}
