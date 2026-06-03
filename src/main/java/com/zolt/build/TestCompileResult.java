package com.zolt.build;

import java.nio.file.Path;

public record TestCompileResult(
        BuildResult buildResult,
        int sourceCount,
        int resourceCount,
        Path outputDirectory,
        String compilerOutput) {
}
