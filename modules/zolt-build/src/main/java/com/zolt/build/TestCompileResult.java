package com.zolt.build;

import java.nio.file.Path;

public record TestCompileResult(
        BuildResult buildResult,
        int sourceCount,
        int resourceCount,
        Path outputDirectory,
        String compilerOutput,
        boolean testCompilationSkipped,
        String testCompilationMode,
        String testIncrementalFallbackReason,
        CompileDiagnostics testCompileDiagnostics,
        long testFingerprintCheckNanos,
        long testFingerprintWriteNanos) {
    public TestCompileResult(
            BuildResult buildResult,
            int sourceCount,
            int resourceCount,
            Path outputDirectory,
            String compilerOutput) {
        this(
                buildResult,
                sourceCount,
                resourceCount,
                outputDirectory,
                compilerOutput,
                false,
                "full",
                "",
                CompileDiagnostics.legacy(sourceCount, false),
                0L,
                0L);
    }

    public TestCompileResult(
            BuildResult buildResult,
            int sourceCount,
            int resourceCount,
            Path outputDirectory,
            String compilerOutput,
            boolean testCompilationSkipped) {
        this(
                buildResult,
                sourceCount,
                resourceCount,
                outputDirectory,
                compilerOutput,
                testCompilationSkipped,
                testCompilationSkipped ? "skipped" : "full",
                "",
                CompileDiagnostics.legacy(sourceCount, testCompilationSkipped),
                0L,
                0L);
    }

    public TestCompileResult(
            BuildResult buildResult,
            int sourceCount,
            int resourceCount,
            Path outputDirectory,
            String compilerOutput,
            boolean testCompilationSkipped,
            long testFingerprintCheckNanos,
            long testFingerprintWriteNanos) {
        this(
                buildResult,
                sourceCount,
                resourceCount,
                outputDirectory,
                compilerOutput,
                testCompilationSkipped,
                testCompilationSkipped ? "skipped" : "full",
                "",
                CompileDiagnostics.legacy(sourceCount, testCompilationSkipped),
                testFingerprintCheckNanos,
                testFingerprintWriteNanos);
    }

    public TestCompileResult {
        testCompilationMode = normalizeMode(testCompilationMode, testCompilationSkipped);
        testIncrementalFallbackReason = testIncrementalFallbackReason == null ? "" : testIncrementalFallbackReason;
        testCompileDiagnostics = testCompileDiagnostics == null ? CompileDiagnostics.empty() : testCompileDiagnostics;
        testFingerprintCheckNanos = Math.max(0L, testFingerprintCheckNanos);
        testFingerprintWriteNanos = Math.max(0L, testFingerprintWriteNanos);
    }

    public long testFingerprintCheckMillis() {
        return testFingerprintCheckNanos / 1_000_000L;
    }

    public long testFingerprintWriteMillis() {
        return testFingerprintWriteNanos / 1_000_000L;
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
