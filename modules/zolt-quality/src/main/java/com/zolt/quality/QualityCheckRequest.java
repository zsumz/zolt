package com.zolt.quality;

import com.zolt.workspace.service.WorkspaceSelectionRequest;
import java.nio.file.Path;
import java.util.List;

public record QualityCheckRequest(
        Path projectRoot,
        Path cacheRoot,
        boolean offline,
        boolean workspace,
        List<String> checks,
        QualityCheckContext context,
        Path reportsDir,
        Path coverageDir,
        boolean requirePackage,
        boolean requirePublishDryRun,
        boolean requireOfflineReady,
        WorkspaceSelectionRequest workspaceSelection) {
    public QualityCheckRequest {
        projectRoot = projectRoot.toAbsolutePath().normalize();
        cacheRoot = cacheRoot.toAbsolutePath().normalize();
        checks = List.copyOf(checks);
    }
}
