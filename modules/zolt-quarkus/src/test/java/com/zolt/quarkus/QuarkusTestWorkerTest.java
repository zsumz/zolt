package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusTestWorkerTest extends QuarkusTestWorkerTestSupport {

    @Test
    void validatesDescriptorThenFailsHonestlyUntilDedicatedRunnerExists() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestRunnerDescriptor descriptor = descriptor();
        QuarkusUnsupportedTest unsupportedTest = new QuarkusUnsupportedTest(
                descriptor.testOutputDirectory().resolve("com/example/HttpTest.class"),
                Path.of("com/example/HttpTest.class"),
                "@QuarkusTest");
        QuarkusTestWorker worker = worker(
                descriptor,
                new QuarkusTestWorkerPlan(
                        descriptor,
                        QuarkusTestWorkerPlanStatus.BLOCKED_UNSUPPORTED_QUARKUS_TESTS,
                        List.of(unsupportedTest)),
                out,
                err);

        int exitCode = worker.run(new String[] {descriptor.descriptorFile().toString()});

        assertEquals(2, exitCode);
        assertTrue(output(out).contains("Runner mode: plain-junit"));
        assertTrue(output(out).contains("Status: blocked by unsupported Quarkus test annotations"));
        assertTrue(output(out).contains("Unsupported Quarkus tests: 1"));
        assertTrue(output(out).contains("com/example/HttpTest.class (@QuarkusTest)"));
        assertTrue(output(err).contains("Quarkus-specific test annotations are not supported"));
        assertTrue(output(err).contains("launcher/session listeners"));
    }

    @Test
    void runsPlainJunitReadyPlan() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestRunnerDescriptor descriptor = descriptor();
        QuarkusTestWorker worker = worker(
                descriptor,
                new QuarkusTestWorkerPlan(
                        descriptor,
                        QuarkusTestWorkerPlanStatus.PLAIN_JUNIT_READY,
                        List.of()),
                descriptorToRun -> new QuarkusPlainJunitWorkerRunner.Result(0, "Tests passed\n"),
                out,
                err);

        int exitCode = worker.run(new String[] {descriptor.descriptorFile().toString()});

        assertEquals(0, exitCode);
        assertTrue(output(out).contains("Status: plain JUnit ready"));
        assertTrue(output(out).contains("Unsupported Quarkus tests: 0"));
        assertTrue(output(out).contains("Tests passed"));
        assertEquals("", output(err));
    }

    @Test
    void routesSelectedQuarkusAnnotationPlanToAnnotationRunner() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestRunnerDescriptor descriptor = descriptor(true);
        QuarkusTestWorker worker = worker(
                descriptor,
                new QuarkusTestWorkerPlan(
                        descriptor,
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of(new QuarkusUnsupportedTest(
                                Path.of("/repo/target/test-classes/com/example/HttpTest.class"),
                                Path.of("com/example/HttpTest.class"),
                                "@QuarkusTest"))),
                descriptorToRun -> new QuarkusPlainJunitWorkerRunner.Result(0, "plain runner should not run\n"),
                plan -> new QuarkusAnnotationWorkerRunner.Result(0, "Quarkus annotation tests passed\n"),
                out,
                err);

        int exitCode = worker.run(new String[] {descriptor.descriptorFile().toString()});

        assertEquals(0, exitCode);
        assertTrue(output(out).contains("Status: Quarkus annotation runner selected"));
        assertTrue(output(out).contains("Unsupported Quarkus tests: 1"));
        assertTrue(output(out).contains("com/example/HttpTest.class (@QuarkusTest)"));
        assertTrue(output(out).contains("Quarkus annotation tests passed"));
        assertEquals("", output(err));
    }

    @Test
    void returnsPlainJunitFailureCodeAndOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestRunnerDescriptor descriptor = descriptor();
        QuarkusTestWorker worker = worker(
                descriptor,
                new QuarkusTestWorkerPlan(
                        descriptor,
                        QuarkusTestWorkerPlanStatus.PLAIN_JUNIT_READY,
                        List.of()),
                descriptorToRun -> new QuarkusPlainJunitWorkerRunner.Result(3, "Tests failed\n"),
                out,
                err);

        int exitCode = worker.run(new String[] {descriptor.descriptorFile().toString()});

        assertEquals(3, exitCode);
        assertTrue(output(out).contains("Tests failed"));
        assertEquals("", output(err));
    }

    @Test
    void unsupportedRunnerModeDiagnosticUsesDescriptorPath() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestRunnerDescriptor descriptor = descriptor(Path.of("/repo/.zolt/build/quarkus"));
        QuarkusTestWorker worker = worker(
                descriptor,
                new QuarkusTestWorkerPlan(
                        descriptor,
                        QuarkusTestWorkerPlanStatus.UNSUPPORTED_RUNNER_MODE,
                        List.of()),
                out,
                err);

        int exitCode = worker.run(new String[] {descriptor.descriptorFile().toString()});

        assertEquals(2, exitCode);
        assertTrue(output(err).contains("/repo/.zolt/build/quarkus/zolt-test-bootstrap.properties"));
        assertFalse(output(err).contains("target/quarkus/zolt-test-bootstrap.properties"));
    }

}
