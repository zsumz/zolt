package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.QuarkusPlanCommandTestSupport.createJarWithTextEntry;
import static com.zolt.cli.QuarkusPlanCommandTestSupport.enableQuarkus;
import static com.zolt.cli.QuarkusPlanCommandTestSupport.quarkusInputFingerprint;
import static com.zolt.cli.QuarkusPlanCommandTestSupport.writeProjectConfig;
import static com.zolt.cli.QuarkusPlanCommandTestSupport.writeQuarkusAugmentationMetadata;
import static com.zolt.cli.QuarkusPlanCommandTestSupport.writeQuarkusPlanLockfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusPlanSuccessTest {
    @TempDir
    private Path tempDir;

    @Test
    void quarkusPlanHelpShowsDirectoryOption() {
        CommandResult result = execute("quarkus", "plan", "--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("--directory"));
        assertTrue(result.stdout().contains("Run as if Zolt was started in the given project"));
        assertTrue(result.stdout().contains("directory."));
        assertEquals("", result.stderr());
    }

    @Test
    void quarkusPlanPrintsAugmentationInputsFromLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, ".zolt/build");
        enableQuarkus(projectDir);
        writeQuarkusPlanLockfile(projectDir);
        Path root = projectDir.toAbsolutePath().normalize();
        Path runtimeJar = cacheRoot.resolve("io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar");
        Path deploymentJar = cacheRoot.resolve(
                "io/quarkus/quarkus-rest-deployment/3.33.0/quarkus-rest-deployment-3.33.0.jar");
        createJarWithTextEntry(
                runtimeJar,
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0\n");

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--directory", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        String inputFingerprint = quarkusInputFingerprint(result.stdout());
        assertTrue(inputFingerprint.matches("sha256:[0-9a-f]{64}"));
        assertEquals("""
                Quarkus augmentation plan
                Status: inputs resolved; augmentation runner not implemented yet
                Application classes: %s
                Package target: fast-jar
                Augmentation output: %s
                Package output: %s
                Input fingerprint: %s
                Augmentation metadata: missing (%s)
                Runtime classpath entries: 1
                  %s
                Deployment classpath entries: 1
                  %s
                Quarkus extensions: 1
                  io.quarkus:quarkus-rest -> io.quarkus:quarkus-rest-deployment:3.33.0
                    runtime jar: %s
                    deployment jar: %s
                Next: implement the Zolt-owned Quarkus augmentation runner with these inputs.
                """.formatted(
                root.resolve(".zolt/build/classes"),
                root.resolve(".zolt/build/quarkus"),
                root.resolve(".zolt/build/quarkus-app"),
                inputFingerprint,
                root.resolve(".zolt/build/quarkus/zolt-augmentation.properties"),
                runtimeJar,
                deploymentJar,
                runtimeJar,
                deploymentJar), result.stdout());
    }

    @Test
    void quarkusPlanReportsCurrentAugmentationMetadata() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir);
        enableQuarkus(projectDir);
        writeQuarkusPlanLockfile(projectDir);
        Path runtimeJar = cacheRoot.resolve("io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar");
        createJarWithTextEntry(
                runtimeJar,
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0\n");
        String fingerprint = quarkusInputFingerprint(execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString()).stdout());
        writeQuarkusAugmentationMetadata(projectDir, fingerprint);

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Augmentation metadata: current"));
        assertTrue(result.stdout().contains("recorded " + fingerprint));
    }

    @Test
    void quarkusPlanReportsStaleAugmentationMetadata() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir);
        enableQuarkus(projectDir);
        writeQuarkusPlanLockfile(projectDir);
        Path runtimeJar = cacheRoot.resolve("io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar");
        createJarWithTextEntry(
                runtimeJar,
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0\n");
        writeQuarkusAugmentationMetadata(projectDir, "sha256:" + "0".repeat(64));

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Augmentation metadata: stale"));
        assertTrue(result.stdout().contains("recorded sha256:" + "0".repeat(64)));
    }
}
