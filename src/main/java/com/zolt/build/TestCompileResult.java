package com.zolt.build;

import java.nio.file.Path;

public record TestCompileResult(
        BuildResult buildResult,
        int sourceCount,
        int resourceCount,
        Path outputDirectory,
        String compilerOutput,
        boolean testCompilationSkipped) {
    public TestCompileResult(
            BuildResult buildResult,
            int sourceCount,
            int resourceCount,
            Path outputDirectory,
            String compilerOutput) {
        this(buildResult, sourceCount, resourceCount, outputDirectory, compilerOutput, false);
    }
}
