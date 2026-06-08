package com.zolt.project;

import java.util.List;

public record BuildSettings(
        String source,
        String test,
        String output,
        String testOutput,
        List<String> testSources,
        BuildMetadataSettings metadata) {
    public BuildSettings {
        testSources = testSources == null ? List.of(test) : List.copyOf(testSources);
        metadata = metadata == null ? BuildMetadataSettings.defaults() : metadata;
    }

    public BuildSettings(
            String source,
            String test,
            String output,
            String testOutput,
            List<String> testSources) {
        this(source, test, output, testOutput, testSources, BuildMetadataSettings.defaults());
    }

    public BuildSettings(
            String source,
            String test,
            String output,
            String testOutput) {
        this(source, test, output, testOutput, List.of(test), BuildMetadataSettings.defaults());
    }

    public static BuildSettings defaults() {
        return new BuildSettings(
                "src/main/java",
                "src/test/java",
                "target/classes",
                "target/test-classes");
    }
}
