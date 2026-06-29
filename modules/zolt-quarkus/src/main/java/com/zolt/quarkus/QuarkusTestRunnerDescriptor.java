package com.zolt.quarkus;

import com.zolt.test.TestSelection;
import com.zolt.build.testruntime.TestJvmArguments;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        List<Path> testRuntimeClasspath,
        TestSelection testSelection,
        TestJvmArguments jvmArguments,
        Map<String, String> environment) {
    public QuarkusTestRunnerDescriptor(
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
        this(
                descriptorFile,
                testRuntimeClasspathFile,
                projectDirectory,
                mainOutputDirectory,
                testOutputDirectory,
                serializedApplicationModel,
                bootstrapDescriptorFile,
                runnerMode,
                supportsQuarkusTestAnnotations,
                jbossLogManagerPresent,
                testRuntimeClasspath,
                TestSelection.empty(),
                TestJvmArguments.empty(),
                Map.of());
    }

    public QuarkusTestRunnerDescriptor(
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
            List<Path> testRuntimeClasspath,
            TestSelection testSelection) {
        this(
                descriptorFile,
                testRuntimeClasspathFile,
                projectDirectory,
                mainOutputDirectory,
                testOutputDirectory,
                serializedApplicationModel,
                bootstrapDescriptorFile,
                runnerMode,
                supportsQuarkusTestAnnotations,
                jbossLogManagerPresent,
                testRuntimeClasspath,
                testSelection,
                TestJvmArguments.empty(),
                Map.of());
    }

    public QuarkusTestRunnerDescriptor(
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
            List<Path> testRuntimeClasspath,
            TestSelection testSelection,
            TestJvmArguments jvmArguments) {
        this(
                descriptorFile,
                testRuntimeClasspathFile,
                projectDirectory,
                mainOutputDirectory,
                testOutputDirectory,
                serializedApplicationModel,
                bootstrapDescriptorFile,
                runnerMode,
                supportsQuarkusTestAnnotations,
                jbossLogManagerPresent,
                testRuntimeClasspath,
                testSelection,
                jvmArguments,
                Map.of());
    }

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
        testSelection = testSelection == null ? TestSelection.empty() : testSelection;
        jvmArguments = jvmArguments == null ? TestJvmArguments.empty() : jvmArguments;
        environment = ordered(environment);
    }

    private static Map<String, String> ordered(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(values)));
    }
}
