package com.zolt.project;

import java.util.List;

public record BuildSettings(
        String source,
        String test,
        String outputRoot,
        String output,
        String testOutput,
        List<String> testSources,
        List<String> groovyTestSources,
        String integrationTestOutput,
        List<String> integrationTestSources,
        List<String> integrationTestResourceRoots,
        List<String> resourceRoots,
        List<String> testResourceRoots,
        ResourceFilteringSettings resourceFiltering,
        TestRuntimeSettings testRuntime,
        BuildMetadataSettings metadata,
        List<GeneratedSourceStep> generatedMainSources,
        List<GeneratedSourceStep> generatedTestSources) {
    private static final String DEFAULT_OUTPUT_ROOT = "target";
    private static final List<String> DEFAULT_RESOURCE_ROOTS = List.of("src/main/resources");
    private static final List<String> DEFAULT_TEST_RESOURCE_ROOTS = List.of("src/test/resources");
    private static final List<String> DEFAULT_INTEGRATION_TEST_SOURCES = List.of("src/integration-test/java");
    private static final List<String> DEFAULT_INTEGRATION_TEST_RESOURCES = List.of("src/integration-test/resources");

    public BuildSettings {
        outputRoot = outputRoot == null ? DEFAULT_OUTPUT_ROOT : outputRoot;
        testSources = copyOrDefault(testSources, List.of(test));
        groovyTestSources = copyOrDefault(groovyTestSources, List.of());
        integrationTestOutput = integrationTestOutput == null ? outputRoot + "/integration-test-classes" : integrationTestOutput;
        integrationTestSources = copyOrDefault(integrationTestSources, DEFAULT_INTEGRATION_TEST_SOURCES);
        integrationTestResourceRoots = copyOrDefault(integrationTestResourceRoots, DEFAULT_INTEGRATION_TEST_RESOURCES);
        resourceRoots = copyOrDefault(resourceRoots, DEFAULT_RESOURCE_ROOTS);
        testResourceRoots = copyOrDefault(testResourceRoots, DEFAULT_TEST_RESOURCE_ROOTS);
        resourceFiltering = resourceFiltering == null ? ResourceFilteringSettings.defaults() : resourceFiltering;
        testRuntime = testRuntime == null ? TestRuntimeSettings.defaults() : testRuntime;
        metadata = metadata == null ? BuildMetadataSettings.defaults() : metadata;
        generatedMainSources = copyOrDefault(generatedMainSources, List.of());
        generatedTestSources = copyOrDefault(generatedTestSources, List.of());
    }

    public BuildSettings(
            String source,
            String test,
            String outputRoot,
            String output,
            String testOutput,
            List<String> testSources,
            List<String> groovyTestSources,
            List<String> resourceRoots,
            List<String> testResourceRoots,
            BuildMetadataSettings metadata) {
        this(source, test, outputRoot, output, testOutput, testSources, groovyTestSources, null, null, null,
                resourceRoots, testResourceRoots, ResourceFilteringSettings.defaults(), TestRuntimeSettings.defaults(),
                metadata, List.of(), List.of());
    }

    public BuildSettings(
            String source,
            String test,
            String output,
            String testOutput,
            List<String> testSources,
            List<String> groovyTestSources,
            String integrationTestOutput,
            List<String> integrationTestSources,
            List<String> integrationTestResourceRoots,
            List<String> resourceRoots,
            List<String> testResourceRoots,
            ResourceFilteringSettings resourceFiltering,
            TestRuntimeSettings testRuntime,
            BuildMetadataSettings metadata,
            List<GeneratedSourceStep> generatedMainSources,
            List<GeneratedSourceStep> generatedTestSources) {
        this(source, test, DEFAULT_OUTPUT_ROOT, output, testOutput, testSources, groovyTestSources,
                integrationTestOutput, integrationTestSources, integrationTestResourceRoots, resourceRoots,
                testResourceRoots, resourceFiltering, testRuntime, metadata, generatedMainSources,
                generatedTestSources);
    }

    public BuildSettings(String source, String test, String outputRoot, String output, String testOutput) {
        this(source, test, outputRoot, output, testOutput, List.of(test), List.of(), null, null, null,
                DEFAULT_RESOURCE_ROOTS, DEFAULT_TEST_RESOURCE_ROOTS, ResourceFilteringSettings.defaults(),
                TestRuntimeSettings.defaults(), BuildMetadataSettings.defaults(), List.of(), List.of());
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
        this(source, test, output, testOutput, testSources, groovyTestSources, null, null, null, resourceRoots,
                testResourceRoots, ResourceFilteringSettings.defaults(), TestRuntimeSettings.defaults(), metadata,
                List.of(), List.of());
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
        this(source, test, output, testOutput, testSources, groovyTestSources, null, null, null, resourceRoots,
                testResourceRoots, resourceFiltering, TestRuntimeSettings.defaults(), metadata, List.of(), List.of());
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
        this(source, test, output, testOutput, testSources, List.of(), null, null, null, resourceRoots,
                testResourceRoots, ResourceFilteringSettings.defaults(), TestRuntimeSettings.defaults(), metadata,
                List.of(), List.of());
    }

    public BuildSettings(
            String source,
            String test,
            String output,
            String testOutput,
            List<String> testSources,
            List<String> groovyTestSources) {
        this(source, test, output, testOutput, testSources, groovyTestSources, null, null, null,
                DEFAULT_RESOURCE_ROOTS, DEFAULT_TEST_RESOURCE_ROOTS, ResourceFilteringSettings.defaults(),
                TestRuntimeSettings.defaults(), BuildMetadataSettings.defaults(), List.of(), List.of());
    }

    public BuildSettings(
            String source,
            String test,
            String output,
            String testOutput,
            List<String> testSources,
            BuildMetadataSettings metadata) {
        this(source, test, output, testOutput, testSources, List.of(), null, null, null, DEFAULT_RESOURCE_ROOTS,
                DEFAULT_TEST_RESOURCE_ROOTS, ResourceFilteringSettings.defaults(), TestRuntimeSettings.defaults(),
                metadata, List.of(), List.of());
    }

    public BuildSettings(String source, String test, String output, String testOutput, List<String> testSources) {
        this(source, test, output, testOutput, testSources, List.of(), null, null, null, DEFAULT_RESOURCE_ROOTS,
                DEFAULT_TEST_RESOURCE_ROOTS, ResourceFilteringSettings.defaults(), TestRuntimeSettings.defaults(),
                BuildMetadataSettings.defaults(), List.of(), List.of());
    }

    public BuildSettings(String source, String test, String output, String testOutput) {
        this(source, test, output, testOutput, List.of(test), List.of(), null, null, null, DEFAULT_RESOURCE_ROOTS,
                DEFAULT_TEST_RESOURCE_ROOTS, ResourceFilteringSettings.defaults(), TestRuntimeSettings.defaults(),
                BuildMetadataSettings.defaults(), List.of(), List.of());
    }

    public static BuildSettings defaults() {
        return new BuildSettings(
                "src/main/java",
                "src/test/java",
                DEFAULT_OUTPUT_ROOT,
                DEFAULT_OUTPUT_ROOT + "/classes",
                DEFAULT_OUTPUT_ROOT + "/test-classes");
    }

    public BuildSettings withGeneratedSources(
            List<GeneratedSourceStep> generatedMainSources,
            List<GeneratedSourceStep> generatedTestSources) {
        return new BuildSettings(source, test, outputRoot, output, testOutput, testSources, groovyTestSources,
                integrationTestOutput, integrationTestSources, integrationTestResourceRoots, resourceRoots,
                testResourceRoots, resourceFiltering, testRuntime, metadata, generatedMainSources,
                generatedTestSources);
    }

    public BuildSettings withResourceFiltering(ResourceFilteringSettings resourceFiltering) {
        return new BuildSettings(source, test, outputRoot, output, testOutput, testSources, groovyTestSources,
                integrationTestOutput, integrationTestSources, integrationTestResourceRoots, resourceRoots,
                testResourceRoots, resourceFiltering, testRuntime, metadata, generatedMainSources,
                generatedTestSources);
    }

    public BuildSettings withTestRuntime(TestRuntimeSettings testRuntime) {
        return new BuildSettings(source, test, outputRoot, output, testOutput, testSources, groovyTestSources,
                integrationTestOutput, integrationTestSources, integrationTestResourceRoots, resourceRoots,
                testResourceRoots, resourceFiltering, testRuntime, metadata, generatedMainSources,
                generatedTestSources);
    }

    public BuildSettings withIntegrationTestSettings(
            String integrationTestOutput,
            List<String> integrationTestSources,
            List<String> integrationTestResourceRoots) {
        return new BuildSettings(source, test, outputRoot, output, testOutput, testSources, groovyTestSources,
                integrationTestOutput, integrationTestSources, integrationTestResourceRoots, resourceRoots,
                testResourceRoots, resourceFiltering, testRuntime, metadata, generatedMainSources,
                generatedTestSources);
    }

    public BuildSettings asIntegrationTestBuild() {
        return new BuildSettings(source,
                integrationTestSources.isEmpty() ? "src/integration-test/java" : integrationTestSources.getFirst(),
                outputRoot, output, integrationTestOutput, integrationTestSources, List.of(), integrationTestOutput,
                integrationTestSources, integrationTestResourceRoots, resourceRoots, integrationTestResourceRoots,
                resourceFiltering, testRuntime, metadata, generatedMainSources, generatedTestSources);
    }

    private static <T> List<T> copyOrDefault(List<T> values, List<T> defaults) {
        return values == null ? defaults : List.copyOf(values);
    }
}
