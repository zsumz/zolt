package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusTestWorkerPlanServiceTest {
    @Test
    void plansPlainJunitReadyWhenConsoleIsPresentAndNoUnsupportedTestsExist() {
        QuarkusTestWorkerPlan plan = new QuarkusTestWorkerPlanService(path -> List.of()).plan(descriptor());

        assertEquals(QuarkusTestWorkerPlanStatus.PLAIN_JUNIT_READY, plan.status());
        assertTrue(plan.plainJunitReady());
        assertTrue(plan.unsupportedTests().isEmpty());
    }

    @Test
    void blocksWhenUnsupportedQuarkusTestsExist() {
        QuarkusUnsupportedTest unsupportedTest = new QuarkusUnsupportedTest(
                Path.of("/repo/target/test-classes/com/example/HttpTest.class"),
                Path.of("com/example/HttpTest.class"),
                "@QuarkusTest");

        QuarkusTestWorkerPlan plan = new QuarkusTestWorkerPlanService(path -> List.of(unsupportedTest))
                .plan(descriptor());

        assertEquals(QuarkusTestWorkerPlanStatus.BLOCKED_UNSUPPORTED_QUARKUS_TESTS, plan.status());
        assertFalse(plan.plainJunitReady());
        assertEquals(List.of(unsupportedTest), plan.unsupportedTests());
    }

    @Test
    void blocksWhenJUnitConsoleIsMissing() {
        QuarkusTestWorkerPlan plan = new QuarkusTestWorkerPlanService(path -> List.of())
                .plan(descriptor(List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit-jupiter-engine.jar"))));

        assertEquals(QuarkusTestWorkerPlanStatus.MISSING_JUNIT_CONSOLE, plan.status());
    }

    @Test
    void blocksUnknownRunnerMode() {
        QuarkusTestWorkerPlan plan = new QuarkusTestWorkerPlanService(path -> List.of())
                .plan(descriptor("quarkus-junit", List.of(Path.of("/cache/junit-platform-console.jar"))));

        assertEquals(QuarkusTestWorkerPlanStatus.UNSUPPORTED_RUNNER_MODE, plan.status());
    }

    private static QuarkusTestRunnerDescriptor descriptor() {
        return descriptor(List.of(
                Path.of("/repo/target/test-classes"),
                Path.of("/repo/target/classes"),
                Path.of("/cache/junit-platform-console.jar")));
    }

    private static QuarkusTestRunnerDescriptor descriptor(List<Path> testRuntimeClasspath) {
        return descriptor(QuarkusTestRunnerRequest.RUNNER_MODE, testRuntimeClasspath);
    }

    private static QuarkusTestRunnerDescriptor descriptor(String runnerMode, List<Path> testRuntimeClasspath) {
        return new QuarkusTestRunnerDescriptor(
                Path.of("/repo/target/quarkus/zolt-test-bootstrap.properties"),
                Path.of("/repo/target/quarkus/test-runtime-classpath.txt"),
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/test-classes"),
                Path.of("/repo/target/quarkus/test-application-model.dat"),
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                runnerMode,
                QuarkusTestRunnerRequest.SUPPORTS_QUARKUS_TEST_ANNOTATIONS,
                true,
                testRuntimeClasspath);
    }
}
