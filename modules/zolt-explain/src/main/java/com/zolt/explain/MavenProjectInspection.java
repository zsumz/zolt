package com.zolt.explain;

import java.nio.file.Path;
import java.util.List;

public record MavenProjectInspection(
        Path path,
        String name,
        String packaging,
        String javaVersion,
        List<String> modules,
        List<String> sourceRoots,
        List<String> testSourceRoots,
        List<String> resourceRoots,
        List<MavenDependencyInspection> dependencies,
        List<MavenDependencyInspection> dependencyManagement,
        List<MavenDependencyInspection> importedBoms,
        List<MavenRepositoryInspection> repositories,
        List<MavenPluginInspection> plugins,
        List<MavenProfileInspection> profiles) {
    public MavenProjectInspection {
        modules = List.copyOf(modules);
        sourceRoots = List.copyOf(sourceRoots);
        testSourceRoots = List.copyOf(testSourceRoots);
        resourceRoots = List.copyOf(resourceRoots);
        dependencies = List.copyOf(dependencies);
        dependencyManagement = List.copyOf(dependencyManagement);
        importedBoms = List.copyOf(importedBoms);
        repositories = List.copyOf(repositories);
        plugins = List.copyOf(plugins);
        profiles = List.copyOf(profiles);
    }
}
