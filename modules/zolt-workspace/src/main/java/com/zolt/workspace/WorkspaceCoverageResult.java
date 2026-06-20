package com.zolt.workspace;

import com.zolt.build.TestRunResult;
import com.zolt.resolve.ResolveResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record WorkspaceCoverageResult(
        Optional<ResolveResult> resolveResult,
        List<WorkspaceBuildResult.MemberBuildResult> builtMembers,
        List<MemberCoverageRunResult> members,
        String reportOutput,
        Path execFile,
        Optional<Path> xmlReport,
        Optional<Path> htmlDirectory,
        int classfileRootCount,
        int sourceRootCount) {
    public WorkspaceCoverageResult {
        resolveResult = resolveResult == null ? Optional.empty() : resolveResult;
        builtMembers = List.copyOf(builtMembers);
        members = List.copyOf(members);
        xmlReport = xmlReport == null ? Optional.empty() : xmlReport;
        htmlDirectory = htmlDirectory == null ? Optional.empty() : htmlDirectory;
    }

    public boolean resolvedLockfile() {
        return resolveResult.isPresent();
    }

    public WorkspaceTestResult testResult() {
        return new WorkspaceTestResult(
                resolveResult,
                builtMembers,
                members.stream()
                        .map(member -> new WorkspaceTestResult.MemberTestRunResult(member.member(), member.result()))
                        .toList());
    }

    public record MemberCoverageRunResult(
            String member,
            TestRunResult result) {
    }
}
