package com.zolt.workspace.run;

import com.zolt.build.run.RunResult;
import com.zolt.resolve.ResolveResult;
import com.zolt.workspace.WorkspaceBuildResult;
import java.util.List;
import java.util.Optional;

public record WorkspaceRunResult(
        Optional<ResolveResult> resolveResult,
        List<WorkspaceBuildResult.MemberBuildResult> builtMembers,
        List<MemberRunResult> members) {
    public WorkspaceRunResult {
        resolveResult = resolveResult == null ? Optional.empty() : resolveResult;
        builtMembers = List.copyOf(builtMembers);
        members = List.copyOf(members);
    }

    public boolean resolvedLockfile() {
        return resolveResult.isPresent();
    }

    public record MemberRunResult(
            String member,
            RunResult result) {
    }
}
