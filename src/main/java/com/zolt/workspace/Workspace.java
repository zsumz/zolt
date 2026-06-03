package com.zolt.workspace;

import java.nio.file.Path;
import java.util.List;

public record Workspace(
        Path root,
        Path configPath,
        WorkspaceConfig config,
        List<WorkspaceMember> members,
        List<WorkspaceProjectEdge> edges) {
    public Workspace {
        members = List.copyOf(members);
        edges = List.copyOf(edges);
    }

    public Workspace(
            Path root,
            Path configPath,
            WorkspaceConfig config,
            List<WorkspaceMember> members) {
        this(root, configPath, config, members, List.of());
    }
}
