package com.zolt.build;

import java.nio.file.Path;

public record TestCompileResult(
        BuildResult buildResult,
        int sourceCount,
        int resourceCount,
        Path outputDirectory,
        String compilerOutput,
        boolean testCompilationSkipped,
        long testFingerprintCheckNanos,
        long testFingerprintWriteNanos) {
    public TestCompileResult(
            BuildResult buildResult,
            int sourceCount,
            int resourceCount,
            Path outputDirectory,
            String compilerOutput) {
        this(buildResult, sourceCount, resourceCount, outputDirectory, compilerOutput, false, 0L, 0L);
    }

    public TestCompileResult(
            BuildResult buildResult,
            int sourceCount,
            int resourceCount,
            Path outputDirectory,
            String compilerOutput,
            boolean testCompilationSkipped) {
        this(buildResult, sourceCount, resourceCount, outputDirectory, compilerOutput, testCompilationSkipped, 0L, 0L);
    }

    public TestCompileResult {
        testFingerprintCheckNanos = Math.max(0L, testFingerprintCheckNanos);
        testFingerprintWriteNanos = Math.max(0L, testFingerprintWriteNanos);
    }

    public long testFingerprintCheckMillis() {
        return testFingerprintCheckNanos / 1_000_000L;
    }

    public long testFingerprintWriteMillis() {
        return testFingerprintWriteNanos / 1_000_000L;
    }
}
