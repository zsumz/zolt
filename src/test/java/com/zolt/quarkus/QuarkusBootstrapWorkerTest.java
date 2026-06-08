package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.QuarkusPackageMode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusBootstrapWorkerTest {
    @TempDir
    private Path projectDir;

    @Test
    void requiresDescriptorArgument() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusBootstrapWorker worker = new QuarkusBootstrapWorker(
                new QuarkusBootstrapDescriptorReader(),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[0]);

        assertEquals(2, exitCode);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("requires exactly one descriptor path"));
    }

    @Test
    void readsDescriptorThenFailsHonestlyUntilBootstrapInvocationExists() {
        QuarkusAugmentationRequest request = request();
        QuarkusBootstrapDescriptor descriptor = new QuarkusBootstrapDescriptorWriter().write(request);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusBootstrapWorker worker = new QuarkusBootstrapWorker(
                new QuarkusBootstrapDescriptorReader(),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[] {descriptor.descriptorFile().toString()});

        assertEquals(3, exitCode);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("bootstrap invocation is not implemented yet"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains(descriptor.descriptorFile().toString()));
    }

    @Test
    void reportsDescriptorReadErrors() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusBootstrapWorker worker = new QuarkusBootstrapWorker(
                new QuarkusBootstrapDescriptorReader(),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[] {projectDir.resolve("missing.properties").toString()});

        assertEquals(1, exitCode);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Could not read Quarkus bootstrap descriptor"));
    }

    private QuarkusAugmentationRequest request() {
        return new QuarkusAugmentationRequest(
                projectDir,
                projectDir.resolve("target/classes"),
                QuarkusPackageMode.FAST_JAR,
                new QuarkusOutputLayout(
                        projectDir.resolve("target/quarkus"),
                        projectDir.resolve("target/quarkus-app")),
                "sha256:" + "1".repeat(64),
                projectDir.resolve("target/quarkus/zolt-augmentation.properties"),
                List.of(projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest.jar")),
                List.of(projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest-deployment.jar")),
                List.of());
    }
}
