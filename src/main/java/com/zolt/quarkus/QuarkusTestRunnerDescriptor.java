package com.zolt.quarkus;

import java.nio.file.Path;
import java.util.List;

public record QuarkusTestRunnerDescriptor(
        Path descriptorFile,
        Path testRuntimeClasspathFile,
        Path projectDirectory,
        Path mainOutputDirectory,
        Path testOutputDirectory,
        Path serializedApplicationModel,
        Path bootstrapDescriptorFile,
        String runnerMode,
        boolean supportsQuarkusTestAnnotations,
        boolean jbossLogManagerPresent,
        List<Path> testRuntimeClasspath) {
    public QuarkusTestRunnerDescriptor {
        if (descriptorFile == null) {
            throw new QuarkusAugmentationException("Quarkus test runner descriptor file is required.");
        }
        if (testRuntimeClasspathFile == null) {
            throw new QuarkusAugmentationException("Quarkus test runner classpath file is required.");
        }
        if (projectDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus test runner project directory is required.");
        }
        if (mainOutputDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus test runner main output directory is required.");
        }
        if (testOutputDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus test runner test output directory is required.");
        }
        if (serializedApplicationModel == null) {
            throw new QuarkusAugmentationException("Quarkus test runner serialized application model is required.");
        }
        if (bootstrapDescriptorFile == null) {
            throw new QuarkusAugmentationException("Quarkus test runner bootstrap descriptor file is required.");
        }
        if (runnerMode == null || runnerMode.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus test runner mode is required.");
        }
        if (testRuntimeClasspath == null || testRuntimeClasspath.isEmpty()) {
            throw new QuarkusAugmentationException("Quarkus test runner classpath is required.");
        }
        testRuntimeClasspath = List.copyOf(testRuntimeClasspath);
    }
}
