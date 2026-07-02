package sh.zolt.workspace.service;

import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.resolve.ResolveResult;
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
