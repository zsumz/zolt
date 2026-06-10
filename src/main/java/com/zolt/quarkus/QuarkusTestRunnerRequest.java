package com.zolt.quarkus;

import com.zolt.build.TestSelection;
import java.nio.file.Path;
import java.util.List;

public record QuarkusTestRunnerRequest(
        Path projectDirectory,
        Path mainOutputDirectory,
        Path testOutputDirectory,
        Path serializedApplicationModel,
        Path bootstrapDescriptorFile,
        List<Path> testRuntimeClasspath,
        boolean jbossLogManagerPresent,
        TestSelection testSelection) {
    public static final String RUNNER_MODE = "plain-junit";
    public static final boolean SUPPORTS_QUARKUS_TEST_ANNOTATIONS = false;

    public QuarkusTestRunnerRequest(
            Path projectDirectory,
            Path mainOutputDirectory,
            Path testOutputDirectory,
            Path serializedApplicationModel,
            Path bootstrapDescriptorFile,
            List<Path> testRuntimeClasspath,
            boolean jbossLogManagerPresent) {
        this(
                projectDirectory,
                mainOutputDirectory,
                testOutputDirectory,
                serializedApplicationModel,
                bootstrapDescriptorFile,
                testRuntimeClasspath,
                jbossLogManagerPresent,
                TestSelection.empty());
    }

    public QuarkusTestRunnerRequest {
        if (projectDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus test runner request requires a project directory.");
        }
        if (mainOutputDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus test runner request requires a main output directory.");
        }
        if (testOutputDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus test runner request requires a test output directory.");
        }
        if (serializedApplicationModel == null) {
            throw new QuarkusAugmentationException("Quarkus test runner request requires a serialized application model.");
        }
        if (bootstrapDescriptorFile == null) {
            throw new QuarkusAugmentationException("Quarkus test runner request requires a bootstrap descriptor file.");
        }
        if (testRuntimeClasspath == null || testRuntimeClasspath.isEmpty()) {
            throw new QuarkusAugmentationException("Quarkus test runner request requires a test runtime classpath.");
        }
        projectDirectory = projectDirectory.toAbsolutePath().normalize();
        mainOutputDirectory = mainOutputDirectory.toAbsolutePath().normalize();
        testOutputDirectory = testOutputDirectory.toAbsolutePath().normalize();
        serializedApplicationModel = serializedApplicationModel.toAbsolutePath().normalize();
        bootstrapDescriptorFile = bootstrapDescriptorFile.toAbsolutePath().normalize();
        testRuntimeClasspath = testRuntimeClasspath.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
        testSelection = testSelection == null ? TestSelection.empty() : testSelection;
    }
}
