package com.zolt.build;

public record TestRunResult(
        TestCompileResult compileResult,
        String output) {
}
