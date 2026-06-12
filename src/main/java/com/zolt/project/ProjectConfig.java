package com.zolt.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record ProjectConfig(
        ProjectMetadata project,
        Map<String, String> repositories,
        Map<String, RepositorySettings> repositorySettings,
        Map<String, RepositoryCredentialSettings> repositoryCredentials,
        Map<String, String> versionAliases,
        Map<String, String> platforms,
        Map<String, String> apiDependencies,
        Set<String> managedApiDependencies,
        Map<String, String> workspaceApiDependencies,
        Map<String, String> dependencies,
        Set<String> managedDependencies,
        Map<String, String> workspaceDependencies,
        Map<String, String> runtimeDependencies,
        Set<String> managedRuntimeDependencies,
        Map<String, String> providedDependencies,
        Set<String> managedProvidedDependencies,
        Map<String, String> devDependencies,
        Set<String> managedDevDependencies,
        Map<String, String> testDependencies,
        Set<String> managedTestDependencies,
        Map<String, String> workspaceTestDependencies,
        Map<String, String> annotationProcessors,
        Set<String> managedAnnotationProcessors,
        Map<String, String> testAnnotationProcessors,
        Set<String> managedTestAnnotationProcessors,
        DependencyPolicySettings dependencyPolicy,
        BuildSettings build,
        NativeSettings nativeSettings,
        CompilerSettings compilerSettings,
        PackageSettings packageSettings,
        FrameworkSettings frameworkSettings,
        Map<String, DependencyMetadata> dependencyMetadata) {
    public static final String MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2";

    public ProjectConfig {
        repositorySettings = repositorySettings == null || repositorySettings.isEmpty()
                ? repositorySettingsFromUrls(repositories)
                : orderedRepositorySettings(repositorySettings);
        repositories = repositories == null || repositories.isEmpty()
                ? repositoryUrls(repositorySettings)
                : orderedMap(repositories);
        repositoryCredentials = orderedRepositoryCredentials(repositoryCredentials);
        versionAliases = orderedMap(versionAliases);
        platforms = orderedMap(platforms);
        apiDependencies = orderedMap(apiDependencies);
        managedApiDependencies = orderedSet(managedApiDependencies);
        workspaceApiDependencies = orderedMap(workspaceApiDependencies);
        dependencies = orderedMap(dependencies);
        managedDependencies = orderedSet(managedDependencies);
        workspaceDependencies = orderedMap(workspaceDependencies);
        runtimeDependencies = orderedMap(runtimeDependencies);
        managedRuntimeDependencies = orderedSet(managedRuntimeDependencies);
        providedDependencies = orderedMap(providedDependencies);
        managedProvidedDependencies = orderedSet(managedProvidedDependencies);
        devDependencies = orderedMap(devDependencies);
        managedDevDependencies = orderedSet(managedDevDependencies);
        testDependencies = orderedMap(testDependencies);
        managedTestDependencies = orderedSet(managedTestDependencies);
        workspaceTestDependencies = orderedMap(workspaceTestDependencies);
        annotationProcessors = orderedMap(annotationProcessors);
        managedAnnotationProcessors = orderedSet(managedAnnotationProcessors);
        testAnnotationProcessors = orderedMap(testAnnotationProcessors);
        managedTestAnnotationProcessors = orderedSet(managedTestAnnotationProcessors);
        dependencyPolicy = dependencyPolicy == null ? DependencyPolicySettings.defaults() : dependencyPolicy;
        nativeSettings = nativeSettings == null ? NativeSettings.defaults() : nativeSettings;
        compilerSettings = compilerSettings == null ? CompilerSettings.defaults() : compilerSettings;
        packageSettings = packageSettings == null ? PackageSettings.defaults() : packageSettings;
        frameworkSettings = frameworkSettings == null ? FrameworkSettings.defaults() : frameworkSettings;
        dependencyMetadata = orderedMetadataMap(dependencyMetadata);
    }

    public ProjectConfig(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, RepositorySettings> repositorySettings,
            Map<String, RepositoryCredentialSettings> repositoryCredentials,
            Map<String, String> platforms,
            Map<String, String> apiDependencies,
            Set<String> managedApiDependencies,
            Map<String, String> workspaceApiDependencies,
            Map<String, String> dependencies,
            Set<String> managedDependencies,
            Map<String, String> workspaceDependencies,
            Map<String, String> runtimeDependencies,
            Set<String> managedRuntimeDependencies,
            Map<String, String> providedDependencies,
            Set<String> managedProvidedDependencies,
            Map<String, String> devDependencies,
            Set<String> managedDevDependencies,
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
            PackageSettings packageSettings,
            FrameworkSettings frameworkSettings,
            Map<String, DependencyMetadata> dependencyMetadata) {
        this(
                project,
                repositories,
                repositorySettings,
                repositoryCredentials,
                Map.of(),
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
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                DependencyPolicySettings.defaults(),
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                frameworkSettings,
                dependencyMetadata);
    }

    public ProjectConfig(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> platforms,
            Map<String, String> apiDependencies,
            Set<String> managedApiDependencies,
            Map<String, String> workspaceApiDependencies,
            Map<String, String> dependencies,
            Set<String> managedDependencies,
            Map<String, String> workspaceDependencies,
            Map<String, String> runtimeDependencies,
            Set<String> managedRuntimeDependencies,
            Map<String, String> providedDependencies,
            Set<String> managedProvidedDependencies,
            Map<String, String> devDependencies,
            Set<String> managedDevDependencies,
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
        this(
                project,
                repositories,
                repositorySettingsFromUrls(repositories),
                Map.of(),
                Map.of(),
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
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                DependencyPolicySettings.defaults(),
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                FrameworkSettings.defaults(),
                Map.of());
    }

    public ProjectConfig(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> platforms,
            Map<String, String> apiDependencies,
            Set<String> managedApiDependencies,
            Map<String, String> workspaceApiDependencies,
            Map<String, String> dependencies,
            Set<String> managedDependencies,
            Map<String, String> workspaceDependencies,
            Map<String, String> runtimeDependencies,
            Set<String> managedRuntimeDependencies,
            Map<String, String> providedDependencies,
            Set<String> managedProvidedDependencies,
            Map<String, String> devDependencies,
            Set<String> managedDevDependencies,
            Map<String, String> testDependencies,
            Set<String> managedTestDependencies,
            Map<String, String> workspaceTestDependencies,
            Map<String, String> annotationProcessors,
            Set<String> managedAnnotationProcessors,
            Map<String, String> testAnnotationProcessors,
            Set<String> managedTestAnnotationProcessors,
            BuildSettings build,
            NativeSettings nativeSettings,
            CompilerSettings compilerSettings) {
        this(
                project,
                repositories,
                repositorySettingsFromUrls(repositories),
                Map.of(),
                Map.of(),
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
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                DependencyPolicySettings.defaults(),
                build,
                nativeSettings,
                compilerSettings,
                PackageSettings.defaults(),
                FrameworkSettings.defaults(),
                Map.of());
    }

    public ProjectConfig(
            ProjectMetadata project,
            Map<String, String> repositories,
            Map<String, String> platforms,
            Map<String, String> apiDependencies,
            Set<String> managedApiDependencies,
            Map<String, String> workspaceApiDependencies,
            Map<String, String> dependencies,
            Set<String> managedDependencies,
            Map<String, String> workspaceDependencies,
            Map<String, String> runtimeDependencies,
            Set<String> managedRuntimeDependencies,
            Map<String, String> providedDependencies,
            Set<String> managedProvidedDependencies,
            Map<String, String> testDependencies,
            Set<String> managedTestDependencies,
            Map<String, String> workspaceTestDependencies,
            Map<String, String> annotationProcessors,
            Set<String> managedAnnotationProcessors,
            Map<String, String> testAnnotationProcessors,
            Set<String> managedTestAnnotationProcessors,
            BuildSettings build,
            NativeSettings nativeSettings,
            CompilerSettings compilerSettings) {
        this(
                project,
                repositories,
                repositorySettingsFromUrls(repositories),
                Map.of(),
                Map.of(),
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
                Map.of(),
                Set.of(),
                testDependencies,
                managedTestDependencies,
                workspaceTestDependencies,
                annotationProcessors,
                managedAnnotationProcessors,
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                DependencyPolicySettings.defaults(),
                build,
                nativeSettings,
                compilerSettings,
                PackageSettings.defaults(),
                FrameworkSettings.defaults(),
                Map.of());
    }

    public ProjectConfig(
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
        this(
                project,
                repositories,
                repositorySettingsFromUrls(repositories),
                Map.of(),
                Map.of(),
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
                DependencyPolicySettings.defaults(),
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                FrameworkSettings.defaults(),
                Map.of());
    }

    public ProjectConfig(
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
        this(
                project,
                repositories,
                repositorySettingsFromUrls(repositories),
                Map.of(),
                Map.of(),
                platforms,
                Map.of(),
                Set.of(),
                Map.of(),
                dependencies,
                managedDependencies,
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                testDependencies,
                managedTestDependencies,
                Map.of(),
                annotationProcessors,
                managedAnnotationProcessors,
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                DependencyPolicySettings.defaults(),
                build,
                nativeSettings,
                compilerSettings,
                PackageSettings.defaults(),
                FrameworkSettings.defaults(),
                Map.of());
    }

    public ProjectConfig(
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
        this(
                project,
                repositories,
                repositorySettingsFromUrls(repositories),
                Map.of(),
                Map.of(),
                platforms,
                Map.of(),
                Set.of(),
                Map.of(),
                dependencies,
                managedDependencies,
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                testDependencies,
                managedTestDependencies,
                Map.of(),
                annotationProcessors,
                managedAnnotationProcessors,
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                DependencyPolicySettings.defaults(),
                build,
                nativeSettings,
                CompilerSettings.defaults(),
                PackageSettings.defaults(),
                FrameworkSettings.defaults(),
                Map.of());
    }

    public ProjectConfig(
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
        this(
                project,
                repositories,
                repositorySettingsFromUrls(repositories),
                Map.of(),
                Map.of(),
                platforms,
                Map.of(),
                Set.of(),
                Map.of(),
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
                DependencyPolicySettings.defaults(),
                build,
                nativeSettings,
                CompilerSettings.defaults(),
                PackageSettings.defaults(),
                FrameworkSettings.defaults(),
                Map.of());
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
                repositorySettingsFromUrls(repositories),
                Map.of(),
                Map.of(),
                platforms,
                Map.of(),
                Set.of(),
                Map.of(),
                dependencies,
                managedDependencies,
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                testDependencies,
                managedTestDependencies,
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                DependencyPolicySettings.defaults(),
                build,
                nativeSettings,
                CompilerSettings.defaults(),
                PackageSettings.defaults(),
                FrameworkSettings.defaults(),
                Map.of());
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
                Map.of(),
                Set.of(),
                Map.of(),
                dependencies,
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                testDependencies,
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                build,
                nativeSettings,
                CompilerSettings.defaults());
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

    public static Map<String, RepositorySettings> defaultRepositorySettings() {
        return repositorySettingsFromUrls(defaultRepositories());
    }

    private static Map<String, String> orderedMap(Map<String, String> values) {
        if (values == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<String, RepositorySettings> orderedRepositorySettings(Map<String, RepositorySettings> values) {
        if (values == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<String, RepositoryCredentialSettings> orderedRepositoryCredentials(
            Map<String, RepositoryCredentialSettings> values) {
        if (values == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<String, RepositorySettings> repositorySettingsFromUrls(Map<String, String> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            return Map.of();
        }
        Map<String, RepositorySettings> settings = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : repositories.entrySet()) {
            settings.put(entry.getKey(), new RepositorySettings(entry.getKey(), entry.getValue(), Optional.empty()));
        }
        return Collections.unmodifiableMap(settings);
    }

    private static Map<String, String> repositoryUrls(Map<String, RepositorySettings> repositorySettings) {
        if (repositorySettings == null || repositorySettings.isEmpty()) {
            return Map.of();
        }
        Map<String, String> urls = new LinkedHashMap<>();
        for (Map.Entry<String, RepositorySettings> entry : repositorySettings.entrySet()) {
            urls.put(entry.getKey(), entry.getValue().url());
        }
        return Collections.unmodifiableMap(urls);
    }

    private static Set<String> orderedSet(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private static Map<String, DependencyMetadata> orderedMetadataMap(Map<String, DependencyMetadata> values) {
        if (values == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public ProjectConfig withBuildSettings(BuildSettings build) {
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
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                dependencyPolicy,
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                frameworkSettings,
                dependencyMetadata);
    }

    public ProjectConfig withVersionAliases(Map<String, String> versionAliases) {
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
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                dependencyPolicy,
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                frameworkSettings,
                dependencyMetadata);
    }

    public ProjectConfig withPackageSettings(PackageSettings packageSettings) {
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
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                dependencyPolicy,
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                frameworkSettings,
                dependencyMetadata);
    }

    public ProjectConfig withFrameworkSettings(FrameworkSettings frameworkSettings) {
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
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                dependencyPolicy,
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                frameworkSettings,
                dependencyMetadata);
    }

    public ProjectConfig withDependencyPolicy(DependencyPolicySettings dependencyPolicy) {
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
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                dependencyPolicy,
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                frameworkSettings,
                dependencyMetadata);
    }

    public ProjectConfig withDependencyMetadata(Map<String, DependencyMetadata> dependencyMetadata) {
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
                testAnnotationProcessors,
                managedTestAnnotationProcessors,
                dependencyPolicy,
                build,
                nativeSettings,
                compilerSettings,
                packageSettings,
                frameworkSettings,
                dependencyMetadata);
    }
}
