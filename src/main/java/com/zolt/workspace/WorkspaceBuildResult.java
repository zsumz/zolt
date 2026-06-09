package com.zolt.workspace;

import com.zolt.build.BuildResult;
import com.zolt.classpath.ClasspathSet;
import com.zolt.resolve.ResolveResult;
import java.util.List;
import java.util.Optional;

public record WorkspaceBuildResult(
        Optional<ResolveResult> resolveResult,
        List<MemberBuildResult> members) {
    public WorkspaceBuildResult {
        resolveResult = resolveResult == null ? Optional.empty() : resolveResult;
        members = List.copyOf(members);
    }

    public boolean resolvedLockfile() {
        return resolveResult.isPresent();
    }

    public int sourceCount() {
        return members.stream()
                .map(MemberBuildResult::result)
                .mapToInt(BuildResult::sourceCount)
                .sum();
    }

    public int mainCompilationSkippedCount() {
        return (int) members.stream()
                .map(MemberBuildResult::result)
                .filter(BuildResult::mainCompilationSkipped)
                .count();
    }

    public int mainCompilationExecutedCount() {
        return members.size() - mainCompilationSkippedCount();
    }

    public record MemberBuildResult(
            String member,
            BuildResult result,
            ClasspathSet classpaths) {
    }
}
