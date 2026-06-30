package com.zolt.explain.gradle;

import java.nio.file.Path;
import java.util.List;

public record GradleProjectInspection(
        Path path,
        String name,
        String buildFile,
        String dsl,
        String javaVersion,
        List<GradlePluginInspection> plugins,
        List<GradleRepositoryInspection> repositories,
        List<GradleDependencyInspection> dependencies,
        List<String> sourceRoots,
        List<String> testSourceRoots) {
    public GradleProjectInspection {
        plugins = List.copyOf(plugins);
        repositories = List.copyOf(repositories);
        dependencies = List.copyOf(dependencies);
        sourceRoots = List.copyOf(sourceRoots);
        testSourceRoots = List.copyOf(testSourceRoots);
    }
}
