package com.zolt.framework;

import java.nio.file.Path;

public record FrameworkRunResult(
        Path runnerJar,
        String runnerDescription) {
    public FrameworkRunResult {
        if (runnerJar == null) {
            throw new IllegalArgumentException("Framework run result requires a runner jar.");
        }
        if (runnerDescription == null || runnerDescription.isBlank()) {
            throw new IllegalArgumentException("Framework run result requires a runner description.");
        }
    }
}
