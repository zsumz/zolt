package com.zolt.explain.maven;

import java.nio.file.Path;
import java.util.List;

/**
 * A statically inspected Maven project.
 *
 * <p>The coordinate fields are named to match Maven's own vocabulary and remove the historical
 * confusion where {@code name} held the artifactId. {@code artifactId} is the Maven
 * {@code <artifactId>} (also the Zolt project name in emitted config); {@code name} is the human
 * {@code <name>} element (empty when the POM declares none).
 */
public record MavenProjectInspection(
        Path path,
        String artifactId,
        String groupId,
        String version,
        String name,
        String packaging,
        String javaVersion,
        String testJavaVersion,
        List<String> modules,
        List<String> sourceRoots,
        List<String> testSourceRoots,
        List<String> resourceRoots,
        List<String> testResourceRoots,
        List<MavenDependencyInspection> dependencies,
        List<MavenDependencyInspection> dependencyManagement,
        List<MavenDependencyInspection> importedBoms,
        List<MavenAnnotationProcessorInspection> annotationProcessors,
        List<MavenParentInspection> parents,
        List<MavenRepositoryInspection> repositories,
        List<MavenPluginInspection> plugins,
        List<MavenProfileInspection> profiles) {
    public MavenProjectInspection {
        groupId = groupId == null ? "" : groupId;
        version = version == null ? "" : version;
        name = name == null ? "" : name;
        testJavaVersion = testJavaVersion == null ? "" : testJavaVersion;
        modules = List.copyOf(modules);
        sourceRoots = List.copyOf(sourceRoots);
        testSourceRoots = List.copyOf(testSourceRoots);
        resourceRoots = List.copyOf(resourceRoots);
        testResourceRoots = List.copyOf(testResourceRoots);
        dependencies = List.copyOf(dependencies);
        dependencyManagement = List.copyOf(dependencyManagement);
        importedBoms = List.copyOf(importedBoms);
        annotationProcessors = List.copyOf(annotationProcessors);
        parents = List.copyOf(parents);
        repositories = List.copyOf(repositories);
        plugins = List.copyOf(plugins);
        profiles = List.copyOf(profiles);
    }
}
