package sh.zolt.workspace.service;

import sh.zolt.build.BuildResult;
import sh.zolt.build.CompileDiagnostics;
import sh.zolt.classpath.ClasspathSet;
import sh.zolt.resolve.ResolveResult;
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

    public long mainFingerprintCheckNanos() {
        return members.stream()
                .map(MemberBuildResult::result)
                .mapToLong(BuildResult::mainFingerprintCheckNanos)
                .sum();
    }

    public long mainFingerprintWriteNanos() {
        return members.stream()
                .map(MemberBuildResult::result)
                .mapToLong(BuildResult::mainFingerprintWriteNanos)
                .sum();
    }

    public long mainFingerprintCheckMillis() {
        return mainFingerprintCheckNanos() / 1_000_000L;
    }

    public long mainFingerprintWriteMillis() {
        return mainFingerprintWriteNanos() / 1_000_000L;
    }

    public CompileDiagnostics mainCompileDiagnostics() {
        return new CompileDiagnostics(
                sumDiagnostics(CompileDiagnostics::sourcesAdded),
                sumDiagnostics(CompileDiagnostics::sourcesChanged),
                sumDiagnostics(CompileDiagnostics::sourcesDeleted),
                sumDiagnostics(CompileDiagnostics::sourcesRecompiled),
                sumDiagnostics(CompileDiagnostics::dependentSourcesRecompiled),
                sumDiagnostics(CompileDiagnostics::classesDeleted),
                sumDiagnostics(CompileDiagnostics::abiChangedClasses),
                sumDiagnostics(CompileDiagnostics::packagePrivateAbiChangedClasses));
    }

    public int workspaceAbiInvalidationCount() {
        return (int) members.stream()
                .map(MemberBuildResult::result)
                .filter(result -> "compile-classpath-changed".equals(result.mainIncrementalFallbackReason()))
                .count();
    }

    private int sumDiagnostics(java.util.function.ToIntFunction<CompileDiagnostics> value) {
        return members.stream()
                .map(MemberBuildResult::result)
                .map(BuildResult::mainCompileDiagnostics)
                .mapToInt(value)
                .sum();
    }

    public record MemberBuildResult(
            String member,
            BuildResult result,
            ClasspathSet classpaths) {
    }
}
