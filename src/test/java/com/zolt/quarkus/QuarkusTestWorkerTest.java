package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusTestWorkerTest {
    @Test
    void requiresDescriptorArgument() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestWorker worker = worker(descriptor(), new ByteArrayOutputStream(), err);

        int exitCode = worker.run(new String[] {});

        assertEquals(2, exitCode);
        assertTrue(output(err).contains("requires a test runner descriptor path"));
    }

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
    void reportsDescriptorReadFailures() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestWorker worker = new QuarkusTestWorker(
                path -> {
                    throw new QuarkusAugmentationException("bad descriptor");
                },
                descriptor -> new QuarkusTestWorkerPlan(
                        descriptor,
                        QuarkusTestWorkerPlanStatus.PLAIN_JUNIT_READY,
                        List.of()),
                descriptor -> new QuarkusPlainJunitWorkerRunner.Result(0, ""),
                plan -> new QuarkusAnnotationWorkerRunner.Result(0, ""),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[] {"/repo/target/quarkus/zolt-test-bootstrap.properties"});

        assertEquals(1, exitCode);
        assertTrue(output(err).contains("error: bad descriptor"));
    }

    @Test
    void reportsPlanFailures() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestWorker worker = new QuarkusTestWorker(
                path -> descriptor(),
                descriptor -> {
                    throw new QuarkusAugmentationException("bad plan");
                },
                descriptor -> new QuarkusPlainJunitWorkerRunner.Result(0, ""),
                plan -> new QuarkusAnnotationWorkerRunner.Result(0, ""),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[] {"/repo/target/quarkus/zolt-test-bootstrap.properties"});

        assertEquals(1, exitCode);
        assertTrue(output(err).contains("error: bad plan"));
    }

    private static QuarkusTestWorker worker(
            QuarkusTestRunnerDescriptor descriptor,
            ByteArrayOutputStream out,
            ByteArrayOutputStream err) {
        return worker(
                descriptor,
                new QuarkusTestWorkerPlan(
                        descriptor,
                        QuarkusTestWorkerPlanStatus.PLAIN_JUNIT_READY,
                        List.of()),
                descriptorToRun -> new QuarkusPlainJunitWorkerRunner.Result(0, ""),
                out,
                err);
    }

    private static QuarkusTestWorker worker(
            QuarkusTestRunnerDescriptor descriptor,
            QuarkusTestWorkerPlan plan,
            ByteArrayOutputStream out,
            ByteArrayOutputStream err) {
        return worker(
                descriptor,
                plan,
                descriptorToRun -> new QuarkusPlainJunitWorkerRunner.Result(0, ""),
                planToRun -> new QuarkusAnnotationWorkerRunner.Result(0, ""),
                out,
                err);
    }

    private static QuarkusTestWorker worker(
            QuarkusTestRunnerDescriptor descriptor,
            QuarkusTestWorkerPlan plan,
            QuarkusTestWorker.PlainJunitRunner plainJunitRunner,
            ByteArrayOutputStream out,
            ByteArrayOutputStream err) {
        return worker(
                descriptor,
                plan,
                plainJunitRunner,
                planToRun -> new QuarkusAnnotationWorkerRunner.Result(0, ""),
                out,
                err);
    }

    private static QuarkusTestWorker worker(
            QuarkusTestRunnerDescriptor descriptor,
            QuarkusTestWorkerPlan plan,
            QuarkusTestWorker.PlainJunitRunner plainJunitRunner,
            QuarkusTestWorker.QuarkusAnnotationRunner quarkusAnnotationRunner,
            ByteArrayOutputStream out,
            ByteArrayOutputStream err) {
        return new QuarkusTestWorker(
                path -> descriptor,
                actualDescriptor -> plan,
                plainJunitRunner,
                quarkusAnnotationRunner,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private static QuarkusTestRunnerDescriptor descriptor() {
        return descriptor(QuarkusTestRunnerRequest.SUPPORTS_QUARKUS_TEST_ANNOTATIONS);
    }

    private static QuarkusTestRunnerDescriptor descriptor(boolean supportsQuarkusTestAnnotations) {
        return new QuarkusTestRunnerDescriptor(
                Path.of("/repo/target/quarkus/zolt-test-bootstrap.properties"),
                Path.of("/repo/target/quarkus/test-runtime-classpath.txt"),
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/test-classes"),
                Path.of("/repo/target/quarkus/test-application-model.dat"),
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                QuarkusTestRunnerRequest.RUNNER_MODE,
                supportsQuarkusTestAnnotations,
                true,
                List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit-platform-console.jar")));
    }

    private static String output(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }
}
