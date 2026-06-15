package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResolveCommandTest {
    @TempDir
    private Path tempDir;

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
        writeProjectConfig(projectDir);

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
        writeProjectConfig(projectDir, """
                [dependencies]
                "com.example:missing" = "1.0.0"
                """);

        CommandResult result = execute(
                "resolve",
                "--offline",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Offline mode requires cached POM"));
        assertTrue(result.stderr().contains("Run the command without --offline"));
    }

    private static void writeProjectConfig(Path projectDir) throws IOException {
        writeProjectConfig(projectDir, "[dependencies]\n");
    }

    private static void writeProjectConfig(Path projectDir, String dependencySection) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo") + """
                main = "com.example.Main"

                [repositories]
                test = "https://repo.maven.apache.org/maven2"

                %s

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """.formatted(dependencySection));
    }
}
