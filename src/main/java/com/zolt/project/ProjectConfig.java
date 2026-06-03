package com.zolt.project;

import java.util.Map;
import java.util.Set;

public record ProjectConfig(
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
    public static final String MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2";

    public ProjectConfig {
        repositories = Map.copyOf(repositories);
        platforms = Map.copyOf(platforms);
        dependencies = Map.copyOf(dependencies);
        managedDependencies = Set.copyOf(managedDependencies);
        testDependencies = Map.copyOf(testDependencies);
        managedTestDependencies = Set.copyOf(managedTestDependencies);
        annotationProcessors = Map.copyOf(annotationProcessors);
        managedAnnotationProcessors = Set.copyOf(managedAnnotationProcessors);
        testAnnotationProcessors = Map.copyOf(testAnnotationProcessors);
        managedTestAnnotationProcessors = Set.copyOf(managedTestAnnotationProcessors);
        nativeSettings = nativeSettings == null ? NativeSettings.defaults() : nativeSettings;
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
                dependencies,
                managedDependencies,
                testDependencies,
                managedTestDependencies,
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                build,
                nativeSettings);
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
                dependencies,
                Set.of(),
                testDependencies,
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                build,
                nativeSettings);
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
}
