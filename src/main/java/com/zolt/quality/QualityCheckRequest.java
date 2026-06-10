package com.zolt.quality;

import com.zolt.workspace.WorkspaceSelectionRequest;
import java.nio.file.Path;
import java.util.List;

public record QualityCheckRequest(
        Path projectRoot,
        boolean workspace,
        List<String> checks,
        WorkspaceSelectionRequest workspaceSelection) {
    public QualityCheckRequest {
        projectRoot = projectRoot.toAbsolutePath().normalize();
        checks = List.copyOf(checks);
    }
}
