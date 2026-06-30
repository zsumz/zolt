package com.zolt.workspace.service;

import java.util.List;

public record WorkspaceSelectionRequest(
        boolean all,
        List<String> members) {
    public WorkspaceSelectionRequest {
        members = members == null ? List.of() : List.copyOf(members);
    }

    public static WorkspaceSelectionRequest defaults() {
        return new WorkspaceSelectionRequest(false, List.of());
    }
}
