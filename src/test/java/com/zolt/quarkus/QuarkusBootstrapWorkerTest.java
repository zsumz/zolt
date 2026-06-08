package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
                new QuarkusBootstrapApiProbe(),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[0]);

        assertEquals(2, exitCode);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("requires exactly one descriptor path"));
    }

    @Test
    void probesBootstrapApiThenFailsHonestlyUntilApplicationModelInvocationExists() throws IOException {
        Path descriptorFile = writeDescriptor();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusBootstrapWorker worker = new QuarkusBootstrapWorker(
                new QuarkusBootstrapDescriptorReader(),
                new QuarkusBootstrapApiProbe(),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[] {descriptorFile.toString()});

        assertEquals(3, exitCode);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("ApplicationModel invocation is not implemented yet"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains(descriptorFile.toString()));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains(WorkerBootstrap.class.getName()));
    }

    @Test
    void reportsDescriptorReadErrors() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuarkusBootstrapWorker worker = new QuarkusBootstrapWorker(
                new QuarkusBootstrapDescriptorReader(),
                new QuarkusBootstrapApiProbe(),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[] {projectDir.resolve("missing.properties").toString()});

        assertEquals(1, exitCode);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Could not read Quarkus bootstrap descriptor"));
    }

    private Path writeDescriptor() throws IOException {
        Path augmentationDirectory = projectDir.resolve("target/quarkus");
        Files.createDirectories(augmentationDirectory);
        Path runtimeClasspathFile = augmentationDirectory.resolve("runtime-classpath.txt");
        Path deploymentClasspathFile = augmentationDirectory.resolve("deployment-classpath.txt");
        Files.writeString(runtimeClasspathFile, "", StandardCharsets.UTF_8);
        Files.writeString(deploymentClasspathFile, "", StandardCharsets.UTF_8);
        Path descriptorFile = augmentationDirectory.resolve("zolt-bootstrap.properties");
        Files.writeString(
                descriptorFile,
                """
                version=1
                bootstrapClass=%s
                augmentActionClass=%s
                mode=prod
                package=fast-jar
                projectDirectory=%s
                applicationClasses=%s
                augmentationDirectory=%s
                packageDirectory=%s
                runtimeClasspathFile=%s
                deploymentClasspathFile=%s
                inputFingerprint=%s
                """.formatted(
                        WorkerBootstrap.class.getName(),
                        WorkerAugmentAction.class.getName(),
                        projectDir,
                        projectDir.resolve("target/classes"),
                        augmentationDirectory,
                        projectDir.resolve("target/quarkus-app"),
                        runtimeClasspathFile,
                        deploymentClasspathFile,
                        "sha256:" + "1".repeat(64)),
                StandardCharsets.UTF_8);
        return descriptorFile;
    }

    public static final class WorkerBootstrap {
        public static Builder builder() {
            return new Builder();
        }

        public void bootstrap() {
        }

        public static final class Builder {
        }
    }

    public interface WorkerAugmentAction {
        Object createProductionApplication();
    }
}
