package com.zolt.quarkus.testworker;

import com.zolt.quarkus.annotation.QuarkusAnnotationWorkerRunner;
import com.zolt.quarkus.annotation.launcher.QuarkusAnnotationJvmRunner;
import com.zolt.quarkus.testworker.descriptor.QuarkusTestRunnerDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

abstract class QuarkusTestWorkerTestSupport {
    static QuarkusTestWorker worker(
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

    static QuarkusTestWorker worker(
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

    static QuarkusTestWorker worker(
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

    static QuarkusTestWorker worker(
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

    static QuarkusTestRunnerDescriptor descriptor() {
        return descriptor(QuarkusTestRunnerRequest.SUPPORTS_QUARKUS_TEST_ANNOTATIONS);
    }

    static QuarkusTestRunnerDescriptor descriptor(boolean supportsQuarkusTestAnnotations) {
        return descriptor(Path.of("/repo/target/quarkus"), supportsQuarkusTestAnnotations);
    }

    static QuarkusTestRunnerDescriptor descriptor(Path quarkusDirectory) {
        return descriptor(quarkusDirectory, QuarkusTestRunnerRequest.SUPPORTS_QUARKUS_TEST_ANNOTATIONS);
    }

    static QuarkusTestRunnerDescriptor descriptor(Path quarkusDirectory, boolean supportsQuarkusTestAnnotations) {
        return new QuarkusTestRunnerDescriptor(
                quarkusDirectory.resolve("zolt-test-bootstrap.properties"),
                quarkusDirectory.resolve("test-runtime-classpath.txt"),
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/test-classes"),
                quarkusDirectory.resolve("test-application-model.dat"),
                quarkusDirectory.resolve("zolt-bootstrap.properties"),
                QuarkusTestRunnerRequest.RUNNER_MODE,
                supportsQuarkusTestAnnotations,
                true,
                List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit-platform-console.jar")));
    }

    static String output(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }
}
