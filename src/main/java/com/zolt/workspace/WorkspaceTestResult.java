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

    public int mainCompilationSkippedCount() {
        return (int) builtMembers.stream()
                .map(WorkspaceBuildResult.MemberBuildResult::result)
                .filter(result -> result.mainCompilationSkipped())
                .count();
    }

    public int mainCompilationExecutedCount() {
        return builtMembers.size() - mainCompilationSkippedCount();
    }

    public int testCompilationSkippedCount() {
        return (int) members.stream()
                .map(MemberTestRunResult::result)
                .map(TestRunResult::compileResult)
                .filter(result -> result.testCompilationSkipped())
                .count();
    }

    public int testCompilationExecutedCount() {
        return members.size() - testCompilationSkippedCount();
    }

    public int testRuntimeClasspathEntryCount() {
        return members.stream()
                .map(MemberTestRunResult::result)
                .mapToInt(TestRunResult::testRuntimeClasspathEntries)
                .sum();
    }

    public int testLauncherClasspathEntryCount() {
        return members.stream()
                .map(MemberTestRunResult::result)
                .mapToInt(TestRunResult::testLauncherClasspathEntries)
                .sum();
    }

    public int testDiscoveryScanRootCount() {
        return members.stream()
                .map(MemberTestRunResult::result)
                .mapToInt(TestRunResult::testDiscoveryScanRoots)
                .sum();
    }

    public record MemberTestRunResult(
            String member,
            TestRunResult result) {
    }
}
