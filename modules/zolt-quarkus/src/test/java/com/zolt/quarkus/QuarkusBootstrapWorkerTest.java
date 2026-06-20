package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusBootstrapWorkerTest {
    @TempDir
    private Path projectDir;

    @Test
    void requiresDescriptorArgument() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusBootstrapWorker worker = QuarkusBootstrapWorkerFixture.worker(
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[0]);

        assertEquals(2, exitCode);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("requires exactly one descriptor path"));
    }

    @Test
    void createsProductionApplicationAndEmitsVerifiedOutputResult() throws IOException {
        Path descriptorFile = QuarkusBootstrapWorkerFixture.writeDescriptor(projectDir);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusBootstrapWorker worker = QuarkusBootstrapWorkerFixture.worker(
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[] {descriptorFile.toString()});

        assertEquals(0, exitCode);
        QuarkusBootstrapWorkerResult result = new QuarkusBootstrapWorkerResultCodec()
                .parse(out.toString(StandardCharsets.UTF_8))
                .orElseThrow();
        assertEquals("sha256:" + "1".repeat(64), result.inputFingerprint());
        assertEquals(projectDir.resolve("target/quarkus-app"), result.packageDirectory());
        assertEquals(projectDir.resolve("target/quarkus-app/quarkus-run.jar"), result.runnerJar());
        assertEquals(projectDir.resolve("target/quarkus-app/lib"), result.libraryDirectory());
        assertEquals(1, result.artifactResultCount());
    }

    @Test
    void reportsDescriptorReadErrors() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusBootstrapWorker worker = QuarkusBootstrapWorkerFixture.worker(
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[] {projectDir.resolve("missing.properties").toString()});

        assertEquals(1, exitCode);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Could not read Quarkus bootstrap descriptor"));
    }
}
