package com.zolt.resolve.support;

import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

final class ResolveTestConfigs {
    private ResolveTestConfigs() {
    }

    static ProjectConfig config(URI baseUri) {
        return configWithDependencies(baseUri, Map.of("com.example:app", "1.0.0"));
    }

    static ProjectConfig configWithDependencies(URI baseUri, Map<String, String> dependencies) {
        return configWithRepositoryAndDependencies(baseUri.toString(), dependencies);
    }

    static ProjectConfig configWithRepository(String repositoryUrl) {
        return configWithRepositoryAndDependencies(repositoryUrl, Map.of("com.example:app", "1.0.0"));
    }

    static ProjectConfig configWithRepositoryAndDependencies(String repositoryUrl, Map<String, String> dependencies) {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", repositoryUrl),
                dependencies,
                Map.of(),
                BuildSettings.defaults());
    }

    static ProjectConfig configWithTestDependencies(URI baseUri, Map<String, String> testDependencies) {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of(),
                testDependencies,
                BuildSettings.defaults());
    }
}
