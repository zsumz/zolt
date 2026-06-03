package com.zolt.project;

import java.util.List;

public record BuildSettings(
        String source,
        String test,
        String output,
        String testOutput,
        List<String> testSources) {
    public BuildSettings {
        testSources = testSources == null ? List.of(test) : List.copyOf(testSources);
    }

    public BuildSettings(
            String source,
            String test,
            String output,
            String testOutput) {
        this(source, test, output, testOutput, List.of(test));
    }

    public static BuildSettings defaults() {
        return new BuildSettings(
                "src/main/java",
                "src/test/java",
                "target/classes",
                "target/test-classes");
    }
}
