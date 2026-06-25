package com.zolt.quarkus;

public final class QuarkusTestPlanFormatter {
    public String format(QuarkusTestPlan plan) {
        StringBuilder output = new StringBuilder();
        output.append("Quarkus test plan\n");
        output.append("Status: ");
        if (plan.hasUnsupportedTests()) {
            output.append("blocked by unsupported Quarkus test annotations\n");
        } else if (plan.hasAnnotationRunnerTests()) {
            output.append("Quarkus annotation runner selected\n");
        } else {
            output.append("plain JUnit tests can run through the current Zolt test runner\n");
        }
        output.append("Test output: ").append(plan.testOutputDirectory()).append('\n');
        output.append("Compiled test output: ").append(plan.testOutputPresent() ? "present" : "missing").append('\n');
        output.append("Serialized application model: ").append(plan.serializedApplicationModel()).append('\n');
        output.append("Test runner descriptor: ").append(plan.testRunnerDescriptor()).append('\n');
        output.append("Quarkus annotation runner tests: ").append(plan.annotationRunnerTests().size()).append('\n');
        for (QuarkusUnsupportedTest test : plan.annotationRunnerTests()) {
            output.append("  ")
                    .append(test.relativePath())
                    .append(" (")
                    .append(test.annotationName())
                    .append(")\n");
        }
        output.append("Unsupported Quarkus tests: ").append(plan.blockedUnsupportedTests().size()).append('\n');
        for (QuarkusUnsupportedTest test : plan.blockedUnsupportedTests()) {
            output.append("  ")
                    .append(test.relativePath())
                    .append(" (")
                    .append(test.annotationName())
                    .append(")\n");
        }
        output.append("Next: ");
        if (plan.hasUnsupportedTests()) {
            output.append("remove unsupported Quarkus integration/main test annotations or use the supported direct `@QuarkusTest` fixture shape.\n");
        } else if (plan.hasAnnotationRunnerTests()) {
            output.append("run `zolt test` to execute supported direct `@QuarkusTest` classes through Zolt's Quarkus annotation runner.\n");
        } else {
            output.append("run `zolt test` for plain JUnit coverage; dedicated Quarkus test bootstrap remains future work.\n");
        }
        return output.toString();
    }
}
