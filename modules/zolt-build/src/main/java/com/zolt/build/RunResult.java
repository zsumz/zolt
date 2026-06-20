package com.zolt.build;

public record RunResult(
        BuildResult buildResult,
        JavaRunResult javaRunResult) {
}
