package com.zolt.project;

import java.util.List;

public record BuildSettings(
        String source,
        String test,
        String output,
        String testOutput,
        List<String> testSources,
        List<String> groovyTestSources,
        List<String> resourceRoots,
        List<String> testResourceRoots,
        ResourceFilteringSettings resourceFiltering,
        TestRuntimeSettings testRuntime,
        BuildMetadataSettings metadata,
        List<GeneratedSourceStep> generatedMainSources,
        List<GeneratedSourceStep> generatedTestSources) {
    public BuildSettings {
        testSources = testSources == null ? List.of(test) : List.copyOf(testSources);
        groovyTestSources = groovyTestSources == null ? List.of() : List.copyOf(groovyTestSources);
        resourceRoots = resourceRoots == null ? List.of("src/main/resources") : List.copyOf(resourceRoots);
        testResourceRoots = testResourceRoots == null ? List.of("src/test/resources") : List.copyOf(testResourceRoots);
        resourceFiltering = resourceFiltering == null ? ResourceFilteringSettings.defaults() : resourceFiltering;
        testRuntime = testRuntime == null ? TestRuntimeSettings.defaults() : testRuntime;
        metadata = metadata == null ? BuildMetadataSettings.defaults() : metadata;
        generatedMainSources = generatedMainSources == null ? List.of() : List.copyOf(generatedMainSources);
        generatedTestSources = generatedTestSources == null ? List.of() : List.copyOf(generatedTestSources);
    }

    public BuildSettings(
            String source,
            String test,
            String output,
            String testOutput,
            List<String> testSources,
            List<String> groovyTestSources,
            List<String> resourceRoots,
            List<String> testResourceRoots,
            BuildMetadataSettings metadata) {
        this(
                source,
                test,
                output,
                testOutput,
                testSources,
                groovyTestSources,
                resourceRoots,
                testResourceRoots,
                ResourceFilteringSettings.defaults(),
                TestRuntimeSettings.defaults(),
                metadata,
                List.of(),
                List.of());
    }

    public BuildSettings(
            String source,
            String test,
            String output,
            String testOutput,
            List<String> testSources,
            List<String> groovyTestSources,
            List<String> resourceRoots,
            List<String> testResourceRoots,
            ResourceFilteringSettings resourceFiltering,
            BuildMetadataSettings metadata) {
        this(
                source,
                test,
                output,
                testOutput,
                testSources,
                groovyTestSources,
                resourceRoots,
                testResourceRoots,
                resourceFiltering,
                TestRuntimeSettings.defaults(),
                metadata,
                List.of(),
                List.of());
    }

    public BuildSettings(
            String source,
            String test,
            String output,
            String testOutput,
            List<String> testSources,
            List<String> resourceRoots,
            List<String> testResourceRoots,
            BuildMetadataSettings metadata) {
        this(
                source,
                test,
                output,
                testOutput,
                testSources,
                List.of(),
                resourceRoots,
                testResourceRoots,
                ResourceFilteringSettings.defaults(),
                TestRuntimeSettings.defaults(),
                metadata,
                List.of(),
                List.of());
    }

    public BuildSettings(
            String source,
            String test,
            String output,
            String testOutput,
            List<String> testSources,
            List<String> groovyTestSources) {
        this(
                source,
                test,
                output,
                testOutput,
                testSources,
                groovyTestSources,
                List.of("src/main/resources"),
                List.of("src/test/resources"),
                ResourceFilteringSettings.defaults(),
                TestRuntimeSettings.defaults(),
                BuildMetadataSettings.defaults(),
                List.of(),
                List.of());
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
                List.of(),
                List.of("src/main/resources"),
                List.of("src/test/resources"),
                ResourceFilteringSettings.defaults(),
                TestRuntimeSettings.defaults(),
                metadata,
                List.of(),
                List.of());
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
                List.of(),
                List.of("src/main/resources"),
                List.of("src/test/resources"),
                ResourceFilteringSettings.defaults(),
                TestRuntimeSettings.defaults(),
                BuildMetadataSettings.defaults(),
                List.of(),
                List.of());
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
                List.of(),
                List.of("src/main/resources"),
                List.of("src/test/resources"),
                ResourceFilteringSettings.defaults(),
                TestRuntimeSettings.defaults(),
                BuildMetadataSettings.defaults(),
                List.of(),
                List.of());
    }

    public static BuildSettings defaults() {
        return new BuildSettings(
                "src/main/java",
                "src/test/java",
                "target/classes",
                "target/test-classes");
    }

    public BuildSettings withGeneratedSources(
            List<GeneratedSourceStep> generatedMainSources,
            List<GeneratedSourceStep> generatedTestSources) {
        return new BuildSettings(
                source,
                test,
                output,
                testOutput,
                testSources,
                groovyTestSources,
                resourceRoots,
                testResourceRoots,
                resourceFiltering,
                testRuntime,
                metadata,
                generatedMainSources,
                generatedTestSources);
    }

    public BuildSettings withResourceFiltering(ResourceFilteringSettings resourceFiltering) {
        return new BuildSettings(
                source,
                test,
                output,
                testOutput,
                testSources,
                groovyTestSources,
                resourceRoots,
                testResourceRoots,
                resourceFiltering,
                testRuntime,
                metadata,
                generatedMainSources,
                generatedTestSources);
    }

    public BuildSettings withTestRuntime(TestRuntimeSettings testRuntime) {
        return new BuildSettings(
                source,
                test,
                output,
                testOutput,
                testSources,
                groovyTestSources,
                resourceRoots,
                testResourceRoots,
                resourceFiltering,
                testRuntime,
                metadata,
                generatedMainSources,
                generatedTestSources);
    }
}
