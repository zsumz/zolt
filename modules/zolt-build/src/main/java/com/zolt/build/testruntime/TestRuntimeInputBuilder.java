package com.zolt.build.testruntime;

import com.zolt.project.TestRuntimeSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TestRuntimeInputBuilder {
    TestRuntimeInputs build(
            Path projectDirectory,
            TestRuntimeSettings settings,
            TestJvmArguments cliJvmArguments,
            List<String> cliEvents) {
        TestRuntimeSettings testRuntimeSettings = settings == null ? TestRuntimeSettings.defaults() : settings;
        TestJvmArguments commandLineArguments = cliJvmArguments == null ? TestJvmArguments.empty() : cliJvmArguments;
        List<String> arguments = new ArrayList<>();
        for (String argument : testRuntimeSettings.jvmArgs()) {
            arguments.add(expandProjectRoot(projectDirectory, argument, "test.runtime.jvmArgs"));
        }
        for (Map.Entry<String, String> entry : testRuntimeSettings.systemProperties().entrySet()) {
            arguments.add("-D"
                    + entry.getKey()
                    + "="
                    + expandProjectRoot(projectDirectory, entry.getValue(), "test.runtime.systemProperties"));
        }
        arguments.addAll(commandLineArguments.values());

        Map<String, String> environment = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : testRuntimeSettings.environment().entrySet()) {
            environment.put(
                    entry.getKey(),
                    expandProjectRoot(projectDirectory, entry.getValue(), "test.runtime.environment"));
        }
        List<String> events = new ArrayList<>(testRuntimeSettings.events());
        if (cliEvents != null) {
            for (String event : cliEvents) {
                try {
                    TestRuntimeSettings.validateEvent("--test-event", event);
                } catch (IllegalArgumentException exception) {
                    throw new TestRunException(exception.getMessage(), exception);
                }
                if (!events.contains(event)) {
                    events.add(event);
                }
            }
        }
        return new TestRuntimeInputs(
                new TestJvmArguments(arguments),
                Collections.unmodifiableMap(environment),
                List.copyOf(events));
    }

    private static String expandProjectRoot(Path projectDirectory, String value, String section) {
        String projectRoot = projectDirectory.toAbsolutePath().normalize().toString();
        String expanded = value.replace("${project.root}", projectRoot);
        if (expanded.contains("${")) {
            throw new TestRunException(
                    "Unsupported placeholder in ["
                            + section
                            + "] value `"
                            + value
                            + "`. Supported placeholder: ${project.root}.");
        }
        return expanded;
    }
}
