package sh.zolt.workspace.service;

import sh.zolt.workspace.WorkspaceConfig;
import java.nio.file.Path;
import java.util.List;

public record Workspace(
        Path root,
        Path configPath,
        WorkspaceConfig config,
        List<WorkspaceMember> members,
        List<WorkspaceProjectEdge> edges,
        List<String> buildOrder) {
    public Workspace {
        members = List.copyOf(members);
        edges = List.copyOf(edges);
        buildOrder = List.copyOf(buildOrder);
    }

    public Workspace(
            Path root,
            Path configPath,
            WorkspaceConfig config,
            List<WorkspaceMember> members,
            List<WorkspaceProjectEdge> edges) {
        this(root, configPath, config, members, edges, memberPaths(members));
    }

    public Workspace(
            Path root,
            Path configPath,
            WorkspaceConfig config,
            List<WorkspaceMember> members) {
        this(root, configPath, config, members, List.of());
    }

    private static List<String> memberPaths(List<WorkspaceMember> members) {
        return members.stream()
                .map(WorkspaceMember::path)
                .toList();
    }
}
