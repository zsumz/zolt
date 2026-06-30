package com.zolt.quarkus.testworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.annotation.QuarkusAnnotationWorkerRunner;
import com.zolt.quarkus.annotation.launcher.QuarkusAnnotationJvmRunner;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusTestWorkerFailuresTest extends QuarkusTestWorkerTestSupport {
    @Test
    void requiresDescriptorArgument() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestWorker worker = worker(descriptor(), new ByteArrayOutputStream(), err);

        int exitCode = worker.run(new String[] {});

        assertEquals(2, exitCode);
        assertTrue(output(err).contains("requires a test runner descriptor path"));
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
}
