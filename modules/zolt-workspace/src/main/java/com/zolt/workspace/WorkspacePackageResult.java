package com.zolt.workspace;

import com.zolt.build.PackageResult;
import com.zolt.resolve.ResolveResult;
import java.util.List;
import java.util.Optional;

public record WorkspacePackageResult(
        Optional<ResolveResult> resolveResult,
        List<WorkspaceBuildResult.MemberBuildResult> builtMembers,
        List<MemberPackageResult> members) {
    public WorkspacePackageResult {
        resolveResult = resolveResult == null ? Optional.empty() : resolveResult;
        builtMembers = List.copyOf(builtMembers);
        members = List.copyOf(members);
    }

    public boolean resolvedLockfile() {
        return resolveResult.isPresent();
    }

    public int entryCount() {
        return members.stream()
                .map(MemberPackageResult::result)
                .mapToInt(PackageResult::entryCount)
                .sum();
    }

    public record MemberPackageResult(
            String member,
            PackageResult result) {
    }
}
