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
    void selectsQuarkusAnnotationRunnerWhenQuarkusTestsExist() {
        QuarkusUnsupportedTest unsupportedTest = new QuarkusUnsupportedTest(
                Path.of("/repo/target/test-classes/com/example/HttpTest.class"),
                Path.of("com/example/HttpTest.class"),
                "@QuarkusTest",
                true);

        QuarkusTestWorkerPlan plan = new QuarkusTestWorkerPlanService(path -> List.of(unsupportedTest))
                .plan(descriptor());

        assertEquals(QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED, plan.status());
        assertFalse(plan.plainJunitReady());
        assertTrue(plan.quarkusTestRunnerSelected());
        assertEquals(List.of(unsupportedTest), plan.unsupportedTests());
    }

    @Test
    void selectsEachAnnotationRunnerClassOnlyOnceWhenSupportedMarkersOverlap() {
        QuarkusUnsupportedTest profileModifier = new QuarkusUnsupportedTest(
                Path.of("/repo/target/test-classes/com/example/ProfiledHttpTest.class"),
                Path.of("com/example/ProfiledHttpTest.class"),
                "@TestProfile",
                true);
        QuarkusUnsupportedTest quarkusTest = new QuarkusUnsupportedTest(
                Path.of("/repo/target/test-classes/com/example/ProfiledHttpTest.class"),
                Path.of("com/example/ProfiledHttpTest.class"),
                "@QuarkusTest",
                true);

        QuarkusTestWorkerPlan plan = new QuarkusTestWorkerPlanService(path -> List.of(profileModifier, quarkusTest))
                .plan(descriptor());

        assertEquals(QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED, plan.status());
        assertEquals(List.of(quarkusTest), plan.annotationRunnerTests());
        assertTrue(plan.blockedUnsupportedTests().isEmpty());
    }

    @Test
    void legacyDescriptorsStillBlockWhenAnnotationSupportIsDisabled() {
        QuarkusUnsupportedTest quarkusTest = new QuarkusUnsupportedTest(
                Path.of("/repo/target/test-classes/com/example/HttpTest.class"),
                Path.of("com/example/HttpTest.class"),
                "@QuarkusTest",
                true);

        QuarkusTestWorkerPlan plan = new QuarkusTestWorkerPlanService(path -> List.of(quarkusTest))
                .plan(descriptor(
                        QuarkusTestRunnerRequest.RUNNER_MODE,
                        false,
                        List.of(Path.of("/cache/junit-platform-console.jar"))));

        assertEquals(QuarkusTestWorkerPlanStatus.QUARKUS_TEST_ANNOTATIONS_DISABLED, plan.status());
        assertFalse(plan.plainJunitReady());
        assertEquals(List.of(quarkusTest), plan.unsupportedTests());
    }

    @Test
    void blocksUnsupportedQuarkusTestResourcesEvenWhenAnnotationSupportIsEnabled() {
        QuarkusUnsupportedTest resourceTest = new QuarkusUnsupportedTest(
                Path.of("/repo/target/test-classes/com/example/ResourceHttpTest.class"),
                Path.of("com/example/ResourceHttpTest.class"),
                "@QuarkusTestResource",
                false);

        QuarkusTestWorkerPlan plan = new QuarkusTestWorkerPlanService(path -> List.of(resourceTest))
                .plan(descriptor());

        assertEquals(QuarkusTestWorkerPlanStatus.BLOCKED_UNSUPPORTED_QUARKUS_TESTS, plan.status());
        assertFalse(plan.plainJunitReady());
        assertFalse(plan.quarkusTestRunnerSelected());
        assertEquals(List.of(resourceTest), plan.blockedUnsupportedTests());
        assertTrue(plan.annotationRunnerTests().isEmpty());
    }

    @Test
    void mixedSupportedAndUnsupportedQuarkusAnnotationsBlockWorker() {
        QuarkusUnsupportedTest quarkusTest = new QuarkusUnsupportedTest(
                Path.of("/repo/target/test-classes/com/example/HttpTest.class"),
                Path.of("com/example/HttpTest.class"),
                "@QuarkusTest",
                true);
        QuarkusUnsupportedTest mainTest = new QuarkusUnsupportedTest(
                Path.of("/repo/target/test-classes/com/example/MainTest.class"),
                Path.of("com/example/MainTest.class"),
                "@TestProfile",
                false);

        QuarkusTestWorkerPlan plan = new QuarkusTestWorkerPlanService(path -> List.of(quarkusTest, mainTest))
                .plan(descriptor());

        assertEquals(QuarkusTestWorkerPlanStatus.BLOCKED_UNSUPPORTED_QUARKUS_TESTS, plan.status());
        assertEquals(List.of(quarkusTest), plan.annotationRunnerTests());
        assertEquals(List.of(mainTest), plan.blockedUnsupportedTests());
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
        return descriptor(
                runnerMode,
                QuarkusTestRunnerRequest.SUPPORTS_QUARKUS_TEST_ANNOTATIONS,
                testRuntimeClasspath);
    }

    private static QuarkusTestRunnerDescriptor descriptor(
            String runnerMode,
            boolean supportsQuarkusTestAnnotations,
            List<Path> testRuntimeClasspath) {
        return new QuarkusTestRunnerDescriptor(
                Path.of("/repo/target/quarkus/zolt-test-bootstrap.properties"),
                Path.of("/repo/target/quarkus/test-runtime-classpath.txt"),
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/test-classes"),
                Path.of("/repo/target/quarkus/test-application-model.dat"),
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                runnerMode,
                supportsQuarkusTestAnnotations,
                true,
                testRuntimeClasspath);
    }
}
