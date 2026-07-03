package sh.zolt.quarkus.testworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.testworker.descriptor.QuarkusTestRunnerDescriptor;
import sh.zolt.quarkus.testplan.QuarkusUnsupportedTest;
import sh.zolt.test.TestSelection;
import sh.zolt.test.runtime.TestJvmArguments;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class QuarkusTestRunnerValueTest {
    @Test
    void requestNormalizesPathsDefaultsNullOptionalsAndSortsEnvironment() {
        Path projectDirectory = Path.of("demo/..");
        Path classpathEntry = Path.of("target/test-classes/../test-classes");

        QuarkusTestRunnerRequest request = new QuarkusTestRunnerRequest(
                projectDirectory,
                Path.of("target/classes"),
                Path.of("target/test-classes"),
                Path.of("target/quarkus/test-application-model.dat"),
                Path.of("target/quarkus/zolt-bootstrap.properties"),
                List.of(classpathEntry),
                true,
                null,
                null,
                Map.of("TZ", "UTC", "JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF-8"));

        assertEquals(projectDirectory.toAbsolutePath().normalize(), request.projectDirectory());
        assertEquals(List.of(classpathEntry.toAbsolutePath().normalize()), request.testRuntimeClasspath());
        assertSame(TestSelection.empty(), request.testSelection());
        assertEquals(TestJvmArguments.empty(), request.jvmArguments());
        assertEquals(List.of("JAVA_TOOL_OPTIONS", "TZ"), new ArrayList<>(request.environment().keySet()));
    }

    @Test
    void requestRejectsMissingRequiredInputsWithActionableMessages() {
        assertTrue(assertThrows(
                        QuarkusAugmentationException.class,
                        () -> new QuarkusTestRunnerRequest(
                                null,
                                Path.of("target/classes"),
                                Path.of("target/test-classes"),
                                Path.of("target/quarkus/test-application-model.dat"),
                                Path.of("target/quarkus/zolt-bootstrap.properties"),
                                List.of(Path.of("target/test-classes")),
                                true))
                .getMessage()
                .contains("requires a project directory"));
        assertTrue(assertThrows(
                        QuarkusAugmentationException.class,
                        () -> new QuarkusTestRunnerRequest(
                                Path.of("."),
                                Path.of("target/classes"),
                                Path.of("target/test-classes"),
                                Path.of("target/quarkus/test-application-model.dat"),
                                Path.of("target/quarkus/zolt-bootstrap.properties"),
                                List.of(),
                                true))
                .getMessage()
                .contains("requires a test runtime classpath"));
    }

    @Test
    void descriptorDefaultsNullOptionalsCopiesClasspathAndSortsEnvironment() {
        List<Path> classpath = new ArrayList<>(List.of(Path.of("/repo/target/test-classes")));

        QuarkusTestRunnerDescriptor descriptor = new QuarkusTestRunnerDescriptor(
                Path.of("/repo/target/quarkus/zolt-test-bootstrap.properties"),
                Path.of("/repo/target/quarkus/test-runtime-classpath.txt"),
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/test-classes"),
                Path.of("/repo/target/quarkus/test-application-model.dat"),
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                QuarkusTestRunnerRequest.RUNNER_MODE,
                true,
                false,
                classpath,
                null,
                null,
                Map.of("B", "2", "A", "1"));
        classpath.add(Path.of("/repo/target/classes"));

        assertEquals(List.of(Path.of("/repo/target/test-classes")), descriptor.testRuntimeClasspath());
        assertSame(TestSelection.empty(), descriptor.testSelection());
        assertEquals(TestJvmArguments.empty(), descriptor.jvmArguments());
        assertEquals(List.of("A", "B"), new ArrayList<>(descriptor.environment().keySet()));
    }

    @Test
    void descriptorRejectsMissingModeAndClasspath() {
        assertTrue(assertThrows(
                        QuarkusAugmentationException.class,
                        () -> descriptor(" ", List.of(Path.of("/repo/target/test-classes"))))
                .getMessage()
                .contains("mode is required"));
        assertTrue(assertThrows(
                        QuarkusAugmentationException.class,
                        () -> descriptor(QuarkusTestRunnerRequest.RUNNER_MODE, List.of()))
                .getMessage()
                .contains("classpath is required"));
    }

    @Test
    void planRejectsMissingDescriptorAndStatusAndCopiesUnsupportedTests() {
        QuarkusTestRunnerDescriptor descriptor = descriptor(
                QuarkusTestRunnerRequest.RUNNER_MODE,
                List.of(Path.of("/repo/target/test-classes")));
        List<QuarkusUnsupportedTest> unsupportedTests = new ArrayList<>(List.of(new QuarkusUnsupportedTest(
                Path.of("/repo/target/test-classes/com/example/HttpTest.class"),
                Path.of("com/example/HttpTest.class"),
                "@QuarkusTest",
                true)));

        QuarkusTestWorkerPlan plan = new QuarkusTestWorkerPlan(
                descriptor,
                QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                unsupportedTests);
        unsupportedTests.clear();

        assertEquals(1, plan.unsupportedTests().size());
        assertTrue(assertThrows(
                        QuarkusAugmentationException.class,
                        () -> new QuarkusTestWorkerPlan(null, QuarkusTestWorkerPlanStatus.PLAIN_JUNIT_READY, List.of()))
                .getMessage()
                .contains("requires a descriptor"));
        assertTrue(assertThrows(
                        QuarkusAugmentationException.class,
                        () -> new QuarkusTestWorkerPlan(descriptor, null, List.of()))
                .getMessage()
                .contains("requires a status"));
    }

    private static QuarkusTestRunnerDescriptor descriptor(String runnerMode, List<Path> classpath) {
        return new QuarkusTestRunnerDescriptor(
                Path.of("/repo/target/quarkus/zolt-test-bootstrap.properties"),
                Path.of("/repo/target/quarkus/test-runtime-classpath.txt"),
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/test-classes"),
                Path.of("/repo/target/quarkus/test-application-model.dat"),
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                runnerMode,
                true,
                false,
                classpath);
    }
}
