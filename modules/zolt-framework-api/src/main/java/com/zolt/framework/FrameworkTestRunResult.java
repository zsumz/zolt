package com.zolt.framework;

public record FrameworkTestRunResult(
        String output,
        boolean supportsFrameworkTestAnnotations,
        int workerClasspathEntries,
        int discoveryScanRoots) {
    public FrameworkTestRunResult {
        output = output == null ? "" : output;
    }
}
