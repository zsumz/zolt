package com.zolt.workspace.service;

import com.zolt.lockfile.ZoltLockfile;
import com.zolt.resolve.ResolveResult;
import java.util.Optional;

public record WorkspaceBuildPlan(
        Workspace workspace,
        WorkspaceSelection selection,
        Optional<ResolveResult> resolveResult,
        ZoltLockfile lockfile) {
    public WorkspaceBuildPlan {
        resolveResult = resolveResult == null ? Optional.empty() : resolveResult;
    }

    public boolean resolvedLockfile() {
        return resolveResult.isPresent();
    }
}
