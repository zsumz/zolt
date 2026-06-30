package com.zolt.workspace.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import com.zolt.resolve.ResolveException;
import com.zolt.workspace.Workspace;
import com.zolt.workspace.WorkspaceConfig;
import com.zolt.workspace.WorkspaceMember;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class WorkspacePolicyMergerTest {
    private final WorkspacePolicyMerger merger = new WorkspacePolicyMerger();

    @Test
    void mergesWorkspaceRepositoriesAndPlatformsBeforeMemberPolicy() {
        WorkspaceMember member = member(
                "app",
                config(
                        orderedMap("central", "https://repo.maven.apache.org/maven2", "internal", "https://repo.example/internal"),
                        orderedMap("com.example:platform", "1.0.0")));
        Workspace workspace = workspace(
                orderedMap("central", "https://repo.maven.apache.org/maven2"),
                orderedMap("com.example:platform", "1.0.0", "com.example:company-platform", "2.0.0"),
                member);

        ProjectConfig merged = merger.merge(workspace, member);

        assertEquals(List.of("central", "internal"), List.copyOf(merged.repositories().keySet()));
        assertEquals("https://repo.example/internal", merged.repositories().get("internal"));
        assertEquals(List.of("central", "internal"), List.copyOf(merged.repositorySettings().keySet()));
        assertEquals("https://repo.example/internal", merged.repositorySettings().get("internal").url());
        assertEquals(
                List.of("com.example:platform", "com.example:company-platform"),
                List.copyOf(merged.platforms().keySet()));
    }

    @Test
    void rejectsConflictingWorkspacePlatformOverride() {
        WorkspaceMember member = member(
                "lib",
                config(
                        orderedMap("central", "https://repo.maven.apache.org/maven2"),
                        orderedMap("com.example:platform", "2.0.0")));
        Workspace workspace = workspace(
                orderedMap("central", "https://repo.maven.apache.org/maven2"),
                orderedMap("com.example:platform", "1.0.0"),
                member);

        ResolveException exception = assertThrows(ResolveException.class, () -> merger.merge(workspace, member));

        assertTrue(exception.getMessage().contains("Workspace platform `com.example:platform`"));
        assertTrue(exception.getMessage().contains("zolt-workspace.toml"));
        assertTrue(exception.getMessage().contains("member `lib` declares `2.0.0`"));
        assertTrue(exception.getMessage().contains("Make the values match"));
    }

    private static Workspace workspace(
            Map<String, String> repositories,
            Map<String, String> platforms,
            WorkspaceMember member) {
        return new Workspace(
                Path.of("/workspace/demo"),
                Path.of("/workspace/demo/zolt-workspace.toml"),
                new WorkspaceConfig("demo", List.of(member.path()), List.of(), repositories, platforms),
                List.of(member));
    }

    private static WorkspaceMember member(String path, ProjectConfig config) {
        return new WorkspaceMember(path, Path.of("/workspace/demo").resolve(path), config);
    }

    private static ProjectConfig config(Map<String, String> repositories, Map<String, String> platforms) {
        return ProjectConfigs.withRuntimeDependencySections(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.empty()),
                repositories,
                platforms,
                Map.of("com.example:app", "1.0.0"),
                Set.of(),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null);
    }

    private static Map<String, String> orderedMap(String... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("orderedMap requires key/value pairs");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put(entries[index], entries[index + 1]);
        }
        return values;
    }
}
