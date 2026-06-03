package com.zolt.workspace;

import java.nio.file.Path;
import java.util.List;

public record Workspace(
        Path root,
        Path configPath,
        WorkspaceConfig config,
        List<WorkspaceMember> members) {
    public Workspace {
        members = List.copyOf(members);
    }
}
