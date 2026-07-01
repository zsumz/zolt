package com.zolt.workspace.resolve;

import com.zolt.project.ProjectConfig;
import com.zolt.project.RepositorySettings;
import com.zolt.resolve.ResolveException;
import com.zolt.workspace.service.Workspace;
import com.zolt.workspace.service.WorkspaceMember;
import java.util.LinkedHashMap;
import java.util.Map;

final class WorkspacePolicyMerger {
    ProjectConfig merge(Workspace workspace, WorkspaceMember member) {
        ProjectConfig config = member.config();
        Map<String, String> repositories = mergedPolicy(
                "repository",
                workspace,
                member,
                workspace.config().repositories(),
                config.repositories());
        return new ProjectConfig(
                config.project(),
                repositories,
                repositorySettings(repositories),
                Map.of(),
                config.versionAliases(),
                mergedPolicy(
                        "platform",
                        workspace,
                        member,
                        workspace.config().platforms(),
                        config.platforms()),
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
                config.workspaceAnnotationProcessors(),
                config.testAnnotationProcessors(),
                config.managedTestAnnotationProcessors(),
                config.workspaceTestAnnotationProcessors(),
                config.dependencyPolicy(),
                config.build(),
                config.nativeSettings(),
                config.compilerSettings(),
                config.packageSettings(),
                config.frameworkSettings(),
                config.dependencyMetadata());
    }

    private static Map<String, String> mergedPolicy(
            String kind,
            Workspace workspace,
            WorkspaceMember member,
            Map<String, String> workspaceValues,
            Map<String, String> memberValues) {
        Map<String, String> merged = new LinkedHashMap<>(workspaceValues);
        for (Map.Entry<String, String> entry : memberValues.entrySet()) {
            String existing = merged.putIfAbsent(entry.getKey(), entry.getValue());
            if (existing != null && !existing.equals(entry.getValue())) {
                throw new ResolveException(
                        "Workspace "
                                + kind
                                + " `"
                                + entry.getKey()
                                + "` has value `"
                                + existing
                                + "` in "
                                + workspace.configPath()
                                + " but member `"
                                + member.path()
                                + "` declares `"
                                + entry.getValue()
                                + "`. Make the values match or remove the member override.");
            }
        }
        return merged;
    }

    private static Map<String, RepositorySettings> repositorySettings(Map<String, String> repositories) {
        Map<String, RepositorySettings> settings = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : repositories.entrySet()) {
            settings.put(entry.getKey(), RepositorySettings.unauthenticated(entry.getKey(), entry.getValue()));
        }
        return settings;
    }
}
