package com.zolt.workspace;

import com.zolt.build.nativeimage.NativeBuildResult;
import com.zolt.resolve.ResolveResult;
import java.util.List;
import java.util.Optional;

public record WorkspaceNativeBuildResult(
        Optional<ResolveResult> resolveResult,
        List<WorkspaceBuildResult.MemberBuildResult> builtMembers,
        List<MemberNativeBuildResult> members) {
    public WorkspaceNativeBuildResult {
        resolveResult = resolveResult == null ? Optional.empty() : resolveResult;
        builtMembers = List.copyOf(builtMembers);
        members = List.copyOf(members);
    }

    public boolean resolvedLockfile() {
        return resolveResult.isPresent();
    }

    public record MemberNativeBuildResult(
            String member,
            NativeBuildResult result) {
    }
}
