package com.zolt.workspace.packaging;

import com.zolt.build.run.RunPackageResult;
import com.zolt.resolve.ResolveResult;
import com.zolt.workspace.WorkspaceBuildResult;
import java.util.List;
import java.util.Optional;

public record WorkspaceRunPackageResult(
        Optional<ResolveResult> resolveResult,
        List<WorkspaceBuildResult.MemberBuildResult> builtMembers,
        List<MemberRunPackageResult> members) {
    public WorkspaceRunPackageResult {
        resolveResult = resolveResult == null ? Optional.empty() : resolveResult;
        builtMembers = List.copyOf(builtMembers);
        members = List.copyOf(members);
    }

    public boolean resolvedLockfile() {
        return resolveResult.isPresent();
    }

    public record MemberRunPackageResult(
            String member,
            RunPackageResult result) {
    }
}
