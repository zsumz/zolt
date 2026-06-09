package com.zolt.build;

public record TestRunResult(
        TestCompileResult compileResult,
        String output,
        int testRuntimeClasspathEntries,
        int testLauncherClasspathEntries,
        int testDiscoveryScanRoots) {
    public TestRunResult(TestCompileResult compileResult, String output) {
        this(compileResult, output, 0, 0, 0);
    }

    public TestRunResult(
            TestCompileResult compileResult,
            String output,
            int testRuntimeClasspathEntries,
            int testLauncherClasspathEntries) {
        this(compileResult, output, testRuntimeClasspathEntries, testLauncherClasspathEntries, 0);
    }
}
