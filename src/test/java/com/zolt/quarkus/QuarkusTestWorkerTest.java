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
        QuarkusTestWorker worker = worker(descriptor, out, err);

        int exitCode = worker.run(new String[] {descriptor.descriptorFile().toString()});

        assertEquals(2, exitCode);
        assertTrue(output(out).contains("Runner mode: plain-junit"));
        assertTrue(output(err).contains("Dedicated Quarkus test worker execution is not implemented yet"));
        assertTrue(output(err).contains("launcher/session listeners"));
    }

    @Test
    void reportsDescriptorReadFailures() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusTestWorker worker = new QuarkusTestWorker(
                path -> {
                    throw new QuarkusAugmentationException("bad descriptor");
                },
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[] {"/repo/target/quarkus/zolt-test-bootstrap.properties"});

        assertEquals(1, exitCode);
        assertTrue(output(err).contains("error: bad descriptor"));
    }

    private static QuarkusTestWorker worker(
            QuarkusTestRunnerDescriptor descriptor,
            ByteArrayOutputStream out,
            ByteArrayOutputStream err) {
        return new QuarkusTestWorker(
                path -> descriptor,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private static QuarkusTestRunnerDescriptor descriptor() {
        return new QuarkusTestRunnerDescriptor(
                Path.of("/repo/target/quarkus/zolt-test-bootstrap.properties"),
                Path.of("/repo/target/quarkus/test-runtime-classpath.txt"),
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/test-classes"),
                Path.of("/repo/target/quarkus/test-application-model.dat"),
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                QuarkusTestRunnerRequest.RUNNER_MODE,
                QuarkusTestRunnerRequest.SUPPORTS_QUARKUS_TEST_ANNOTATIONS,
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
