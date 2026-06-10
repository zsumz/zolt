package com.zolt.build;

public record TestRunResult(
        TestCompileResult compileResult,
        String output,
        String testRunner,
        int testRuntimeClasspathEntries,
        int testLauncherClasspathEntries,
        int testDiscoveryScanRoots) {
    public TestRunResult(TestCompileResult compileResult, String output) {
        this(compileResult, output, "unknown", 0, 0, 0);
    }

    public TestRunResult(
            TestCompileResult compileResult,
            String output,
            int testRuntimeClasspathEntries,
            int testLauncherClasspathEntries) {
        this(compileResult, output, "unknown", testRuntimeClasspathEntries, testLauncherClasspathEntries, 0);
    }

    public TestRunResult(
            TestCompileResult compileResult,
            String output,
            int testRuntimeClasspathEntries,
            int testLauncherClasspathEntries,
            int testDiscoveryScanRoots) {
        this(
                compileResult,
                output,
                "unknown",
                testRuntimeClasspathEntries,
                testLauncherClasspathEntries,
                testDiscoveryScanRoots);
    }
}
