package com.zolt.build;

import com.zolt.resolve.ResolveResult;
import java.nio.file.Path;
import java.util.Optional;

public record BuildResult(
        Optional<ResolveResult> resolveResult,
        int sourceCount,
        int resourceCount,
        Path outputDirectory,
        String compilerOutput,
        boolean mainCompilationSkipped,
        long mainFingerprintCheckNanos,
        long mainFingerprintWriteNanos) {
    public BuildResult(
            Optional<ResolveResult> resolveResult,
            int sourceCount,
            int resourceCount,
            Path outputDirectory,
            String compilerOutput) {
        this(resolveResult, sourceCount, resourceCount, outputDirectory, compilerOutput, false, 0L, 0L);
    }

    public BuildResult(
            Optional<ResolveResult> resolveResult,
            int sourceCount,
            int resourceCount,
            Path outputDirectory,
            String compilerOutput,
            boolean mainCompilationSkipped) {
        this(resolveResult, sourceCount, resourceCount, outputDirectory, compilerOutput, mainCompilationSkipped, 0L, 0L);
    }

    public BuildResult {
        resolveResult = resolveResult == null ? Optional.empty() : resolveResult;
        mainFingerprintCheckNanos = Math.max(0L, mainFingerprintCheckNanos);
        mainFingerprintWriteNanos = Math.max(0L, mainFingerprintWriteNanos);
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
}
