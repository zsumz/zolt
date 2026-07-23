package sh.zolt.build;

import sh.zolt.resolve.ResolveResult;
import java.nio.file.Path;
import java.util.Optional;

public record BuildResult(
        Optional<ResolveResult> resolveResult,
        int sourceCount,
        int resourceCount,
        Path outputDirectory,
        String compilerOutput,
        boolean mainCompilationSkipped,
        String mainCompilationMode,
        String mainIncrementalFallbackReason,
        CompileDiagnostics mainCompileDiagnostics,
        long mainFingerprintCheckNanos,
        long mainFingerprintWriteNanos,
        int mainRestoredClassCount,
        String mainBuildCacheOutcome) {
    public BuildResult(
            Optional<ResolveResult> resolveResult,
            int sourceCount,
            int resourceCount,
            Path outputDirectory,
            String compilerOutput) {
        this(
                resolveResult,
                sourceCount,
                resourceCount,
                outputDirectory,
                compilerOutput,
                false,
                "full",
                "",
                CompileDiagnostics.legacy(sourceCount, false),
                0L,
                0L,
                0,
                "");
    }

    public BuildResult(
            Optional<ResolveResult> resolveResult,
            int sourceCount,
            int resourceCount,
            Path outputDirectory,
            String compilerOutput,
            boolean mainCompilationSkipped) {
        this(
                resolveResult,
                sourceCount,
                resourceCount,
                outputDirectory,
                compilerOutput,
                mainCompilationSkipped,
                mainCompilationSkipped ? "skipped" : "full",
                "",
                CompileDiagnostics.legacy(sourceCount, mainCompilationSkipped),
                0L,
                0L,
                0,
                "");
    }

    public BuildResult(
            Optional<ResolveResult> resolveResult,
            int sourceCount,
            int resourceCount,
            Path outputDirectory,
            String compilerOutput,
            boolean mainCompilationSkipped,
            long mainFingerprintCheckNanos,
            long mainFingerprintWriteNanos) {
        this(
                resolveResult,
                sourceCount,
                resourceCount,
                outputDirectory,
                compilerOutput,
                mainCompilationSkipped,
                mainCompilationSkipped ? "skipped" : "full",
                "",
                CompileDiagnostics.legacy(sourceCount, mainCompilationSkipped),
                mainFingerprintCheckNanos,
                mainFingerprintWriteNanos,
                0,
                "");
    }

    public BuildResult {
        resolveResult = resolveResult == null ? Optional.empty() : resolveResult;
        mainCompilationMode = normalizeMode(mainCompilationMode, mainCompilationSkipped);
        mainIncrementalFallbackReason = mainIncrementalFallbackReason == null ? "" : mainIncrementalFallbackReason;
        mainCompileDiagnostics = mainCompileDiagnostics == null ? CompileDiagnostics.empty() : mainCompileDiagnostics;
        mainFingerprintCheckNanos = Math.max(0L, mainFingerprintCheckNanos);
        mainFingerprintWriteNanos = Math.max(0L, mainFingerprintWriteNanos);
        mainRestoredClassCount = Math.max(0, mainRestoredClassCount);
        mainBuildCacheOutcome = mainBuildCacheOutcome == null ? "" : mainBuildCacheOutcome;
    }

    /** Whether this module's compiled output was restored from the build cache instead of compiled. */
    public boolean mainCompilationRestored() {
        return "restored".equals(mainCompilationMode);
    }

    public boolean resolvedLockfile() {
        return resolveResult.isPresent();
    }

    public long mainFingerprintCheckMillis() {
        return mainFingerprintCheckNanos / 1_000_000L;
    }

    public long mainFingerprintWriteMillis() {
        return mainFingerprintWriteNanos / 1_000_000L;
    }

    private static String normalizeMode(String mode, boolean skipped) {
        if (skipped) {
            return "skipped";
        }
        if (mode == null || mode.isBlank()) {
            return "full";
        }
        return mode;
    }
}
