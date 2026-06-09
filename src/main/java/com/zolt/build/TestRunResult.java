package com.zolt.build;

public record TestRunResult(
        TestCompileResult compileResult,
        String output,
        int testRuntimeClasspathEntries,
        int testLauncherClasspathEntries) {
    public TestRunResult(TestCompileResult compileResult, String output) {
        this(compileResult, output, 0, 0);
    }
}
