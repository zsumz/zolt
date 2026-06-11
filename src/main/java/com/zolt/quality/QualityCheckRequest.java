package com.zolt.quality;

import com.zolt.workspace.WorkspaceSelectionRequest;
import java.nio.file.Path;
import java.util.List;

public record QualityCheckRequest(
        Path projectRoot,
        Path cacheRoot,
        boolean offline,
        boolean workspace,
        List<String> checks,
        QualityCheckContext context,
        WorkspaceSelectionRequest workspaceSelection) {
    public QualityCheckRequest {
        projectRoot = projectRoot.toAbsolutePath().normalize();
        cacheRoot = cacheRoot.toAbsolutePath().normalize();
        checks = List.copyOf(checks);
    }
}
