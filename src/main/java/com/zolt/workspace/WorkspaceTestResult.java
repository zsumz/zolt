package com.zolt.workspace;

import com.zolt.build.TestRunResult;
import com.zolt.resolve.ResolveResult;
import java.util.List;
import java.util.Optional;

public record WorkspaceTestResult(
        Optional<ResolveResult> resolveResult,
        List<WorkspaceBuildResult.MemberBuildResult> builtMembers,
        List<MemberTestRunResult> members) {
    public WorkspaceTestResult {
        resolveResult = resolveResult == null ? Optional.empty() : resolveResult;
        builtMembers = List.copyOf(builtMembers);
        members = List.copyOf(members);
    }

    public boolean resolvedLockfile() {
        return resolveResult.isPresent();
    }

    public int mainSourceCount() {
        return builtMembers.stream()
                .map(WorkspaceBuildResult.MemberBuildResult::result)
                .mapToInt(result -> result.sourceCount())
                .sum();
    }

    public int testSourceCount() {
        return members.stream()
                .map(MemberTestRunResult::result)
                .map(TestRunResult::compileResult)
                .mapToInt(result -> result.sourceCount())
                .sum();
    }

    public record MemberTestRunResult(
            String member,
            TestRunResult result) {
    }
}
