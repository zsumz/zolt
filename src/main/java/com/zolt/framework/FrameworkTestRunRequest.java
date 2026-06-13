package com.zolt.framework;

import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record FrameworkTestRunRequest(
        Path projectDirectory,
        ProjectConfig config,
        Path mainOutputDirectory,
        Path testOutputDirectory,
        List<Path> testRuntimeClasspath,
        Path javaExecutable,
        FrameworkTestSelection testSelection,
        List<String> jvmArguments,
        Map<String, String> environment) {
    public FrameworkTestRunRequest {
        if (projectDirectory == null) {
            throw new IllegalArgumentException("Framework test run request requires a project directory.");
        }
        if (config == null) {
            throw new IllegalArgumentException("Framework test run request requires project config.");
        }
        if (mainOutputDirectory == null) {
            throw new IllegalArgumentException("Framework test run request requires a main output directory.");
        }
        if (testOutputDirectory == null) {
            throw new IllegalArgumentException("Framework test run request requires a test output directory.");
        }
        if (testRuntimeClasspath == null) {
            testRuntimeClasspath = List.of();
        }
        if (javaExecutable == null) {
            throw new IllegalArgumentException("Framework test run request requires a Java executable.");
        }
        testRuntimeClasspath = List.copyOf(testRuntimeClasspath);
        testSelection = testSelection == null ? FrameworkTestSelection.empty() : testSelection;
        jvmArguments = jvmArguments == null ? List.of() : List.copyOf(jvmArguments);
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }
}
