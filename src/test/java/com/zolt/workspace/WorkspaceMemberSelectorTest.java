package com.zolt.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class WorkspaceMemberSelectorTest {
    private final WorkspaceMemberSelector selector = new WorkspaceMemberSelector();

    @Test
    void selectsRequestedMembersAndWorkspaceDependenciesInBuildOrder() {
        Workspace workspace = workspace(List.of("apps/api", "modules/core", "apps/worker"), List.of());

        WorkspaceSelection selection = selector.select(
                workspace,
                new WorkspaceSelectionRequest(false, List.of("apps/api")));

        assertEquals(List.of("modules/core", "apps/api"), selection.includedMembers());
        assertEquals(List.of("apps/api"), selection.selectedMembers());
    }

    @Test
    void selectsAllMembersWhenAllIsRequested() {
        Workspace workspace = workspace(List.of("apps/api", "modules/core", "apps/worker"), List.of());

        WorkspaceSelection selection = selector.select(
                workspace,
                new WorkspaceSelectionRequest(true, List.of()));

        assertEquals(List.of("modules/core", "apps/api", "apps/worker"), selection.includedMembers());
        assertEquals(List.of("modules/core", "apps/api", "apps/worker"), selection.selectedMembers());
    }

    @Test
    void usesDefaultMembersWhenNoExplicitSelectionIsProvided() {
        Workspace workspace = workspace(List.of("apps/api", "modules/core", "apps/worker"), List.of("apps/api"));

        WorkspaceSelection selection = selector.select(
                workspace,
                WorkspaceSelectionRequest.defaults());

        assertEquals(List.of("modules/core", "apps/api"), selection.includedMembers());
        assertEquals(List.of("apps/api"), selection.selectedMembers());
    }

    @Test
    void selectsAllMembersWhenNoExplicitSelectionOrDefaultsExist() {
        Workspace workspace = workspace(List.of("apps/api", "modules/core", "apps/worker"), List.of());

        WorkspaceSelection selection = selector.select(
                workspace,
                WorkspaceSelectionRequest.defaults());

        assertEquals(List.of("modules/core", "apps/api", "apps/worker"), selection.includedMembers());
        assertEquals(List.of("modules/core", "apps/api", "apps/worker"), selection.selectedMembers());
    }

    @Test
    void rejectsUnknownRequestedMembers() {
        Workspace workspace = workspace(List.of("apps/api", "modules/core"), List.of());

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> selector.select(workspace, new WorkspaceSelectionRequest(false, List.of("apps/missing"))));

        assertEquals(
                "Workspace member `apps/missing` is not declared in [workspace].members. Choose a declared member or use --all.",
                exception.getMessage());
    }

    @Test
    void rejectsConflictingAllAndMemberSelection() {
        Workspace workspace = workspace(List.of("apps/api", "modules/core"), List.of());

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> selector.select(workspace, new WorkspaceSelectionRequest(true, List.of("apps/api"))));

        assertEquals("Use either --all or --member for workspace selection, not both.", exception.getMessage());
    }

    private static Workspace workspace(List<String> members, List<String> defaultMembers) {
        List<WorkspaceMember> workspaceMembers = members.stream()
                .map(member -> new WorkspaceMember(
                        member,
                        Path.of(member),
                        config(member.substring(member.lastIndexOf('/') + 1))))
                .toList();
        return new Workspace(
                Path.of("."),
                Path.of("zolt-workspace.toml"),
                new WorkspaceConfig("workspace", members, defaultMembers, Map.of(), Map.of()),
                workspaceMembers,
                List.of(new WorkspaceProjectEdge("apps/api", "modules/core", "compile", "com.acme:core")),
                List.of("modules/core", "apps/api", "apps/worker").stream()
                        .filter(members::contains)
                        .toList());
    }

    private static ProjectConfig config(String name) {
        return new ProjectConfig(
                new ProjectMetadata(name, "0.1.0", "com.acme", "21", Optional.empty()),
                Map.of(),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }
}
