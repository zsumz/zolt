package com.zolt.quarkus.testworker;

import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.testplan.QuarkusUnsupportedTest;
import com.zolt.quarkus.testplan.QuarkusUnsupportedTestScanner;
import java.nio.file.Path;
import java.util.List;

public final class QuarkusTestWorkerPlanService {
    private final UnsupportedTestScanner unsupportedTestScanner;

    public QuarkusTestWorkerPlanService() {
        this(new QuarkusUnsupportedTestScanner()::scan);
    }

    QuarkusTestWorkerPlanService(UnsupportedTestScanner unsupportedTestScanner) {
        if (unsupportedTestScanner == null) {
            throw new QuarkusAugmentationException("Quarkus test worker plan scanner is required.");
        }
        this.unsupportedTestScanner = unsupportedTestScanner;
    }

    public QuarkusTestWorkerPlan plan(QuarkusTestRunnerDescriptor descriptor) {
        if (descriptor == null) {
            throw new QuarkusAugmentationException("Quarkus test worker plan requires a descriptor.");
        }
        List<QuarkusUnsupportedTest> unsupportedTests = unsupportedTestScanner.scan(descriptor.testOutputDirectory());
        return new QuarkusTestWorkerPlan(descriptor, status(descriptor, unsupportedTests), unsupportedTests);
    }

    private static QuarkusTestWorkerPlanStatus status(
            QuarkusTestRunnerDescriptor descriptor,
            List<QuarkusUnsupportedTest> unsupportedTests) {
        if (!QuarkusTestRunnerRequest.RUNNER_MODE.equals(descriptor.runnerMode())) {
            return QuarkusTestWorkerPlanStatus.UNSUPPORTED_RUNNER_MODE;
        }
        if (descriptor.testRuntimeClasspath().stream().noneMatch(QuarkusTestWorkerPlanService::isConsoleJar)) {
            return QuarkusTestWorkerPlanStatus.MISSING_JUNIT_CONSOLE;
        }
        if (!unsupportedTests.isEmpty()) {
            if (unsupportedTests.stream().anyMatch(QuarkusUnsupportedTest::blocksAnnotationRunner)) {
                return QuarkusTestWorkerPlanStatus.BLOCKED_UNSUPPORTED_QUARKUS_TESTS;
            }
            if (descriptor.supportsQuarkusTestAnnotations()
                    && unsupportedTests.stream().anyMatch(QuarkusUnsupportedTest::annotationRunnerSupported)) {
                return QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED;
            }
            return QuarkusTestWorkerPlanStatus.QUARKUS_TEST_ANNOTATIONS_DISABLED;
        }
        return QuarkusTestWorkerPlanStatus.PLAIN_JUNIT_READY;
    }

    private static boolean isConsoleJar(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.startsWith("junit-platform-console") && name.endsWith(".jar");
    }

    @FunctionalInterface
    interface UnsupportedTestScanner {
        List<QuarkusUnsupportedTest> scan(Path testOutputDirectory);
    }
}
