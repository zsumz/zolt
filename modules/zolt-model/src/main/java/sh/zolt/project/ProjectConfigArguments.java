package sh.zolt.project;

import java.util.Map;
import java.util.Set;

record ProjectConfigArguments(
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
        Map<String, String> workspaceAnnotationProcessors,
        Map<String, String> testAnnotationProcessors,
        Set<String> managedTestAnnotationProcessors,
        Map<String, String> workspaceTestAnnotationProcessors,
        DependencyPolicySettings dependencyPolicy,
        BuildSettings build,
        NativeSettings nativeSettings,
        CompilerSettings compilerSettings,
        PackageSettings packageSettings,
        FrameworkSettings frameworkSettings,
        Map<String, DependencyMetadata> dependencyMetadata) {
}
