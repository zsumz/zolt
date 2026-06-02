package com.zolt.project;

import java.util.Map;

public record ProjectConfig(
        ProjectMetadata project,
        Map<String, String> repositories,
        Map<String, String> dependencies,
        Map<String, String> testDependencies,
        BuildSettings build) {
    public static final String MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2";

    public ProjectConfig {
        repositories = Map.copyOf(repositories);
        dependencies = Map.copyOf(dependencies);
        testDependencies = Map.copyOf(testDependencies);
    }

    public static Map<String, String> defaultRepositories() {
        return Map.of("central", MAVEN_CENTRAL);
    }
}
