package com.zolt.toml;

import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.NativeSettings;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.RepositoryCredentialSettings;
import com.zolt.project.RepositorySettings;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ProjectConfigFixture {
    private ProjectMetadata project =
            new ProjectMetadata("hello", "0.1.0", "com.example", "21", Optional.of("com.example.Main"));
    private Map<String, String> repositories = ProjectConfig.defaultRepositories();
    private Map<String, RepositorySettings> repositorySettings = Map.of();
    private Map<String, RepositoryCredentialSettings> repositoryCredentials = Map.of();
    private Map<String, String> versionAliases = Map.of();
    private Map<String, String> platforms = Map.of();
    private Map<String, String> apiDependencies = Map.of();
    private Set<String> managedApiDependencies = Set.of();
    private Map<String, String> workspaceApiDependencies = Map.of();
    private Map<String, String> dependencies = Map.of();
    private Set<String> managedDependencies = Set.of();
    private Map<String, String> workspaceDependencies = Map.of();
    private Map<String, String> runtimeDependencies = Map.of();
    private Set<String> managedRuntimeDependencies = Set.of();
    private Map<String, String> providedDependencies = Map.of();
    private Set<String> managedProvidedDependencies = Set.of();
    private Map<String, String> devDependencies = Map.of();
    private Set<String> managedDevDependencies = Set.of();
    private Map<String, String> testDependencies = Map.of();
    private Set<String> managedTestDependencies = Set.of();
    private Map<String, String> workspaceTestDependencies = Map.of();
    private Map<String, String> annotationProcessors = Map.of();
    private Set<String> managedAnnotationProcessors = Set.of();
    private Map<String, String> workspaceAnnotationProcessors = Map.of();
    private Map<String, String> testAnnotationProcessors = Map.of();
    private Set<String> managedTestAnnotationProcessors = Set.of();
    private Map<String, String> workspaceTestAnnotationProcessors = Map.of();
    private DependencyPolicySettings dependencyPolicy = DependencyPolicySettings.defaults();
    private BuildSettings build = BuildSettings.defaults();
    private NativeSettings nativeSettings = NativeSettings.defaults();
    private CompilerSettings compilerSettings = CompilerSettings.defaults();
    private PackageSettings packageSettings = PackageSettings.defaults();
    private FrameworkSettings frameworkSettings = FrameworkSettings.defaults();
    private Map<String, DependencyMetadata> dependencyMetadata = Map.of();

    private ProjectConfigFixture() {
    }

    static ProjectConfigFixture config() {
        return new ProjectConfigFixture();
    }

    ProjectConfigFixture project(String name, String group, String javaVersion, Optional<String> main) {
        this.project = new ProjectMetadata(name, "0.1.0", group, javaVersion, main);
        return this;
    }

    ProjectConfigFixture repositorySettings(Map<String, RepositorySettings> repositorySettings) {
        this.repositories = Map.of();
        this.repositorySettings = repositorySettings;
        return this;
    }

    ProjectConfigFixture repositoryCredentials(
            Map<String, RepositoryCredentialSettings> repositoryCredentials) {
        this.repositoryCredentials = repositoryCredentials;
        return this;
    }

    ProjectConfigFixture platforms(Map<String, String> platforms) {
        this.platforms = platforms;
        return this;
    }

    ProjectConfigFixture managedDependencies(Set<String> managedDependencies) {
        this.managedDependencies = managedDependencies;
        return this;
    }

    ProjectConfigFixture managedTestDependencies(Set<String> managedTestDependencies) {
        this.managedTestDependencies = managedTestDependencies;
        return this;
    }

    ProjectConfigFixture annotationProcessors(Map<String, String> annotationProcessors) {
        this.annotationProcessors = annotationProcessors;
        return this;
    }

    ProjectConfigFixture managedAnnotationProcessors(Set<String> managedAnnotationProcessors) {
        this.managedAnnotationProcessors = managedAnnotationProcessors;
        return this;
    }

    ProjectConfigFixture testAnnotationProcessors(Map<String, String> testAnnotationProcessors) {
        this.testAnnotationProcessors = testAnnotationProcessors;
        return this;
    }

    ProjectConfigFixture managedTestAnnotationProcessors(Set<String> managedTestAnnotationProcessors) {
        this.managedTestAnnotationProcessors = managedTestAnnotationProcessors;
        return this;
    }

    ProjectConfigFixture build(BuildSettings build) {
        this.build = build;
        return this;
    }

    ProjectConfigFixture nativeSettings(NativeSettings nativeSettings) {
        this.nativeSettings = nativeSettings;
        return this;
    }

    ProjectConfigFixture compilerSettings(CompilerSettings compilerSettings) {
        this.compilerSettings = compilerSettings;
        return this;
    }

    ProjectConfig build() {
        return new ProjectConfig(
                project,
                repositories,
                repositorySettings,
                repositoryCredentials,
                versionAliases,
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
                workspaceAnnotationProcessors,
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                workspaceTestAnnotationProcessors,
                dependencyPolicy,
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                frameworkSettings,
                dependencyMetadata);
    }
}
