package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static com.zolt.cli.ResolveCommandTestSupport.writeProjectConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResolveCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void resolveReadsConfigWritesLockfileAndPrintsSummary() throws IOException {
        try (CliTestRepository repository = CliTestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"));

            CommandResult result = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Resolved 1 packages"));
            assertTrue(result.stdout().contains("Downloaded 2 artifacts"));
            assertTrue(result.stdout().contains("Conflicts 0"));
            assertTrue(result.stdout().contains("Wrote " + projectDir.resolve("zolt.lock")));
            assertEquals("", result.stderr());
            assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
        }
    }

    @Test
    void resolveEmitsSparseProgressOnlyWhenEnabled() throws IOException {
        try (CliTestRepository repository = CliTestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("progress-demo");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"));

            CommandResult progress = execute(
                    "--progress=always",
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());
            CommandResult disabled = execute(
                    "--progress=always",
                    "--no-progress",
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            assertEquals(0, progress.exitCode());
            assertTrue(progress.stderr().contains("Resolving dependencies..."));
            assertTrue(progress.stderr().contains("Resolved 1 packages"));
            assertFalse(progress.stderr().contains("\u001B["));
            assertEquals(0, disabled.exitCode());
            assertEquals("", disabled.stderr());
        }
    }

    @Test
    void resolveColorsOnlyProgressLeadFragmentsWhenForced() throws IOException {
        try (CliTestRepository repository = CliTestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("color-progress-demo");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"));

            CommandResult result = execute(
                    "--color=always",
                    "--progress=always",
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            assertEquals(0, result.exitCode());
            assertTrue(result.stderr().contains("\u001B[36mResolving\u001B[0m dependencies..."));
            assertTrue(result.stderr().contains("\u001B[32mResolved\u001B[0m 1 packages"));
            assertFalse(result.stderr().contains("\u001B[36mResolving dependencies\u001B[0m"));
            assertFalse(result.stderr().contains("\u001B[32mResolved 1 packages\u001B[0m"));
            assertTrue(result.stdout().contains("\u001B[32mResolved\u001B[0m 1 packages"));
            assertTrue(result.stdout().contains("\u001B[32mWrote\u001B[0m " + projectDir.resolve("zolt.lock")));
            assertTrue(result.stdout().contains("Next: \u001B[36mzolt build\u001B[0m"));
            assertFalse(result.stdout().contains("\u001B[32mResolved 1 packages\u001B[0m"));
            assertFalse(result.stdout().contains("\u001B[32mWrote " + projectDir.resolve("zolt.lock") + "\u001B[0m"));
        }
    }

    @Test
    void resolveLockedVerifiesExistingLockfile() throws IOException {
        try (CliTestRepository repository = CliTestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"));
            CommandResult unlocked = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            CommandResult locked = execute(
                    "resolve",
                    "--locked",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            assertEquals(0, unlocked.exitCode());
            assertEquals(0, locked.exitCode());
            assertTrue(locked.stdout().contains("Verified " + projectDir.resolve("zolt.lock")));
        }
    }

    @Test
    void resolveRejectsUnknownRepositoryOverlay() throws IOException {
        Path projectDir = tempDir.resolve("resolve-unknown-overlay");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("resolve-unknown-overlay"));

        CommandResult result = execute(
                "resolve",
                "--cwd", projectDir.toString(),
                "--repository-overlay", "gradle-cache");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Unsupported repository overlay `gradle-cache`"));
        assertTrue(result.stderr().contains("Supported overlays: maven-local"));
    }

    @Test
    void resolveRejectsConflictingLocalOverlayOptions() throws IOException {
        Path projectDir = tempDir.resolve("resolve-conflicting-overlays");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("resolve-conflicting-overlays"));

        CommandResult result = execute(
                "resolve",
                "--cwd", projectDir.toString(),
                "--repository-overlay", "local-maven",
                "--no-local-overlays");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Cannot combine local repository overlays with local-overlay rejection"));
        assertTrue(result.stderr().contains("Remove --repository-overlay or remove --no-local-overlays"));
    }

    @Test
    void resolveLockedReportsMissingLockfileClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, Map.of());

        CommandResult result = execute(
                "resolve",
                "--locked",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Locked resolve requires zolt.lock"));
        assertTrue(result.stderr().contains("Run `zolt resolve` to create it"));
    }

    @Test
    void resolveOfflineReportsMissingCachedArtifactClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2", Map.of("com.example:missing", "1.0.0"));

        CommandResult result = execute(
                "resolve",
                "--offline",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Offline mode requires cached POM"));
        assertTrue(result.stderr().contains("Run the command without --offline"));
    }

}
