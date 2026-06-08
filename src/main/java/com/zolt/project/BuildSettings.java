package com.zolt.project;

import java.util.List;

public record BuildSettings(
        String source,
        String test,
        String output,
        String testOutput,
        List<String> testSources,
        List<String> resourceRoots,
        List<String> testResourceRoots,
        BuildMetadataSettings metadata) {
    public BuildSettings {
        testSources = testSources == null ? List.of(test) : List.copyOf(testSources);
        resourceRoots = resourceRoots == null ? List.of("src/main/resources") : List.copyOf(resourceRoots);
        testResourceRoots = testResourceRoots == null ? List.of("src/test/resources") : List.copyOf(testResourceRoots);
        metadata = metadata == null ? BuildMetadataSettings.defaults() : metadata;
    }

    public BuildSettings(
            String source,
            String test,
            String output,
            String testOutput,
            List<String> testSources,
            BuildMetadataSettings metadata) {
        this(
                source,
                test,
                output,
                testOutput,
                testSources,
                List.of("src/main/resources"),
                List.of("src/test/resources"),
                metadata);
    }

    public BuildSettings(
            String source,
            String test,
            String output,
            String testOutput,
            List<String> testSources) {
        this(
                source,
                test,
                output,
                testOutput,
                testSources,
                List.of("src/main/resources"),
                List.of("src/test/resources"),
                BuildMetadataSettings.defaults());
    }

    public BuildSettings(
            String source,
            String test,
            String output,
            String testOutput) {
        this(
                source,
                test,
                output,
                testOutput,
                List.of(test),
                List.of("src/main/resources"),
                List.of("src/test/resources"),
                BuildMetadataSettings.defaults());
    }

    public static BuildSettings defaults() {
        return new BuildSettings(
                "src/main/java",
                "src/test/java",
                "target/classes",
                "target/test-classes");
    }
}
