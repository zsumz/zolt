package com.zolt.quarkus;

import java.nio.file.Path;
import java.util.List;

public record QuarkusTestPlan(
        Path projectDirectory,
        Path testOutputDirectory,
        boolean testOutputPresent,
        Path serializedApplicationModel,
        List<QuarkusUnsupportedTest> unsupportedTests) {
    public QuarkusTestPlan {
        if (projectDirectory == null) {
            throw new QuarkusPlanException("Quarkus test plan requires a project directory.");
        }
        if (testOutputDirectory == null) {
            throw new QuarkusPlanException("Quarkus test plan requires a test output directory.");
        }
        if (serializedApplicationModel == null) {
            throw new QuarkusPlanException("Quarkus test plan requires a serialized application model path.");
        }
        unsupportedTests = unsupportedTests == null ? List.of() : List.copyOf(unsupportedTests);
    }

    public boolean hasUnsupportedTests() {
        return !unsupportedTests.isEmpty();
    }
}
