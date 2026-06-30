package com.zolt.project;

import java.util.Map;

final class ProjectConfigUpdater {
    private ProjectConfigUpdater() {
    }

    static ProjectConfig withBuildSettings(ProjectConfig config, BuildSettings build) {
        return copy(
                config,
                config.versionAliases(),
                config.dependencyPolicy(),
                build,
                config.packageSettings(),
                config.frameworkSettings(),
                config.dependencyMetadata());
    }

    static ProjectConfig withVersion(ProjectConfig config, String version) {
        ProjectMetadata current = config.project();
        ProjectMetadata updated = new ProjectMetadata(
                current.name(),
                version,
                current.group(),
                current.java(),
                current.main());
        return new ProjectConfig(
                updated,
                config.repositories(),
                config.repositorySettings(),
                config.repositoryCredentials(),
                config.versionAliases(),
                config.platforms(),
                config.apiDependencies(),
                config.managedApiDependencies(),
                config.workspaceApiDependencies(),
                config.dependencies(),
                config.managedDependencies(),
                config.workspaceDependencies(),
                config.runtimeDependencies(),
                config.managedRuntimeDependencies(),
                config.providedDependencies(),
                config.managedProvidedDependencies(),
                config.devDependencies(),
                config.managedDevDependencies(),
                config.testDependencies(),
                config.managedTestDependencies(),
                config.workspaceTestDependencies(),
                config.annotationProcessors(),
                config.managedAnnotationProcessors(),
                config.testAnnotationProcessors(),
                config.managedTestAnnotationProcessors(),
                config.dependencyPolicy(),
                config.build(),
                config.nativeSettings(),
                config.compilerSettings(),
                config.packageSettings(),
                config.frameworkSettings(),
                config.dependencyMetadata());
    }

    static ProjectConfig withVersionAliases(ProjectConfig config, Map<String, String> versionAliases) {
        return copy(
                config,
                versionAliases,
                config.dependencyPolicy(),
                config.build(),
                config.packageSettings(),
                config.frameworkSettings(),
                config.dependencyMetadata());
    }

    static ProjectConfig withPackageSettings(ProjectConfig config, PackageSettings packageSettings) {
        return copy(
                config,
                config.versionAliases(),
                config.dependencyPolicy(),
                config.build(),
                packageSettings,
                config.frameworkSettings(),
                config.dependencyMetadata());
    }

    static ProjectConfig withFrameworkSettings(ProjectConfig config, FrameworkSettings frameworkSettings) {
        return copy(
                config,
                config.versionAliases(),
                config.dependencyPolicy(),
                config.build(),
                config.packageSettings(),
                frameworkSettings,
                config.dependencyMetadata());
    }

    static ProjectConfig withDependencyPolicy(ProjectConfig config, DependencyPolicySettings dependencyPolicy) {
        return copy(
                config,
                config.versionAliases(),
                dependencyPolicy,
                config.build(),
                config.packageSettings(),
                config.frameworkSettings(),
                config.dependencyMetadata());
    }

    static ProjectConfig withDependencyMetadata(ProjectConfig config, Map<String, DependencyMetadata> dependencyMetadata) {
        return copy(
                config,
                config.versionAliases(),
                config.dependencyPolicy(),
                config.build(),
                config.packageSettings(),
                config.frameworkSettings(),
                dependencyMetadata);
    }

    private static ProjectConfig copy(
            ProjectConfig config,
            Map<String, String> versionAliases,
            DependencyPolicySettings dependencyPolicy,
            BuildSettings build,
            PackageSettings packageSettings,
            FrameworkSettings frameworkSettings,
            Map<String, DependencyMetadata> dependencyMetadata) {
        return new ProjectConfig(
                config.project(),
                config.repositories(),
                config.repositorySettings(),
                config.repositoryCredentials(),
                versionAliases,
                config.platforms(),
                config.apiDependencies(),
                config.managedApiDependencies(),
                config.workspaceApiDependencies(),
                config.dependencies(),
                config.managedDependencies(),
                config.workspaceDependencies(),
                config.runtimeDependencies(),
                config.managedRuntimeDependencies(),
                config.providedDependencies(),
                config.managedProvidedDependencies(),
                config.devDependencies(),
                config.managedDevDependencies(),
                config.testDependencies(),
                config.managedTestDependencies(),
                config.workspaceTestDependencies(),
                config.annotationProcessors(),
                config.managedAnnotationProcessors(),
                config.testAnnotationProcessors(),
                config.managedTestAnnotationProcessors(),
                dependencyPolicy,
                build,
                config.nativeSettings(),
                config.compilerSettings(),
                packageSettings,
                frameworkSettings,
                dependencyMetadata);
    }
}
