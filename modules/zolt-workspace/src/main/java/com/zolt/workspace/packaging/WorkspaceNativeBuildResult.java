package com.zolt.workspace.packaging;

import com.zolt.build.nativeimage.NativeBuildResult;
import com.zolt.resolve.ResolveResult;
import com.zolt.workspace.service.WorkspaceBuildResult;
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
