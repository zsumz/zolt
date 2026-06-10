package com.zolt.build;

public record TestRunResult(
        TestCompileResult compileResult,
        String output,
        String testRunner,
        int testRuntimeClasspathEntries,
        int testLauncherClasspathEntries,
        int testDiscoveryScanRoots,
        long testRunnerStartupNanos,
        long testRunnerRequestNanos,
        TestSelection testSelection,
        TestJvmArguments testJvmArguments) {
    public TestRunResult {
        testSelection = testSelection == null ? TestSelection.empty() : testSelection;
        testJvmArguments = testJvmArguments == null ? TestJvmArguments.empty() : testJvmArguments;
    }

    public TestRunResult(
            TestCompileResult compileResult,
            String output,
            String testRunner,
            int testRuntimeClasspathEntries,
            int testLauncherClasspathEntries,
            int testDiscoveryScanRoots,
            long testRunnerStartupNanos,
            long testRunnerRequestNanos) {
        this(
                compileResult,
                output,
                testRunner,
                testRuntimeClasspathEntries,
                testLauncherClasspathEntries,
                testDiscoveryScanRoots,
                testRunnerStartupNanos,
                testRunnerRequestNanos,
                TestSelection.empty(),
                TestJvmArguments.empty());
    }

    public TestRunResult(
            TestCompileResult compileResult,
            String output,
            String testRunner,
            int testRuntimeClasspathEntries,
            int testLauncherClasspathEntries,
            int testDiscoveryScanRoots,
            long testRunnerStartupNanos,
            long testRunnerRequestNanos,
            TestSelection testSelection) {
        this(
                compileResult,
                output,
                testRunner,
                testRuntimeClasspathEntries,
                testLauncherClasspathEntries,
                testDiscoveryScanRoots,
                testRunnerStartupNanos,
                testRunnerRequestNanos,
                testSelection,
                TestJvmArguments.empty());
    }

    public TestRunResult(TestCompileResult compileResult, String output) {
        this(compileResult, output, "unknown", 0, 0, 0, -1L, -1L);
    }

    public TestRunResult(
            TestCompileResult compileResult,
            String output,
            int testRuntimeClasspathEntries,
            int testLauncherClasspathEntries) {
        this(compileResult, output, "unknown", testRuntimeClasspathEntries, testLauncherClasspathEntries, 0, -1L, -1L);
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
                testDiscoveryScanRoots,
                -1L,
                -1L);
    }

    public TestRunResult(
            TestCompileResult compileResult,
            String output,
            String testRunner,
            int testRuntimeClasspathEntries,
            int testLauncherClasspathEntries,
            int testDiscoveryScanRoots) {
        this(
                compileResult,
                output,
                testRunner,
                testRuntimeClasspathEntries,
                testLauncherClasspathEntries,
                testDiscoveryScanRoots,
                -1L,
                -1L);
    }
}
