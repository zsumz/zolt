package com.zolt.project;

import java.util.Map;
import java.util.Set;

final class ProjectConfigDependencyConstruction {
    private ProjectConfigDependencyConstruction() {
    }

    static ProjectConfigArguments dependencySectionsWithPackage(
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
        return ProjectConfigConstruction.fullWithPackage(
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
                packageSettings);
    }

    static ProjectConfigArguments dependencySections(
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
        return dependencySectionsWithPackage(
                project,
                repositories,
                platforms,
                Map.of(),
                Set.of(),
                Map.of(),
                dependencies,
                managedDependencies,
                Map.of(),
                testDependencies,
                managedTestDependencies,
                Map.of(),
                annotationProcessors,
                managedAnnotationProcessors,
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                build,
                nativeSettings,
                compilerSettings,
                PackageSettings.defaults());
    }

    static ProjectConfigArguments workspaceDependencySections(
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
        return dependencySectionsWithPackage(
                project,
                repositories,
                platforms,
                Map.of(),
                Set.of(),
                Map.of(),
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
                CompilerSettings.defaults(),
                PackageSettings.defaults());
    }

    static ProjectConfigArguments runtimeDependencySections(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> platforms,
            Map<String, String> dependencies,
            Set<String> managedDependencies,
            Map<String, String> testDependencies,
            Set<String> managedTestDependencies,
            BuildSettings build,
            NativeSettings nativeSettings) {
        return dependencySectionsWithPackage(
                project,
                repositories,
                platforms,
                Map.of(),
                Set.of(),
                Map.of(),
                dependencies,
                managedDependencies,
                Map.of(),
                testDependencies,
                managedTestDependencies,
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                build,
                nativeSettings,
                CompilerSettings.defaults(),
                PackageSettings.defaults());
    }

    static ProjectConfigArguments directDependencies(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> dependencies,
            Map<String, String> testDependencies,
            BuildSettings build,
            NativeSettings nativeSettings) {
        return dependencySections(
                project,
                repositories,
                Map.of(),
                dependencies,
                Set.of(),
                testDependencies,
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                build,
                nativeSettings,
                CompilerSettings.defaults());
    }
}
