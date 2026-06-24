package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
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

final class ResolveCommandOutputTest {
    @TempDir
    private Path tempDir;

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
                    "--color=never",
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
            assertFalse(progress.stdout().contains("\u001B["));
            assertEquals(0, disabled.exitCode());
            assertEquals("", disabled.stderr());
        }
    }

    @Test
    void quietSuppressesHumanSummaryAndDefaultProgress() throws IOException {
        try (CliTestRepository repository = CliTestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("quiet-demo");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"));

            CommandResult quiet = execute(
                    "--quiet",
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());
            CommandResult forcedProgress = execute(
                    "--quiet",
                    "--progress=always",
                    "resolve",
                    "--locked",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            assertEquals(0, quiet.exitCode());
            assertEquals("", quiet.stdout());
            assertEquals("", quiet.stderr());
            assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
            assertEquals(0, forcedProgress.exitCode());
            assertEquals("", forcedProgress.stdout());
            assertTrue(forcedProgress.stderr().contains("Resolving dependencies..."));
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
}
