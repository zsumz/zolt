package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void resolvePrintsTextTimingsWhenRequested() throws IOException {
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
                    "--timings",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Resolved 1 packages"));
            assertTrue(result.stderr().contains("Timings for zolt resolve"));
            assertTrue(result.stderr().contains("config read:"));
            assertTrue(result.stderr().contains("resolve graph:"));
            assertTrue(result.stderr().contains("resolvedPackages=1"));
            assertTrue(result.stderr().contains("downloadedArtifacts=2"));
        }
    }

    @Test
    void resolvePrintsJsonTimingsWhenRequested() throws IOException {
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
                    "--timings",
                    "--timings-format", "json",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Resolved 1 packages"));
            String[] lines = result.stderr().lines().toArray(String[]::new);
            assertEquals(2, lines.length);
            assertTrue(lines[0].startsWith("{\"command\":\"resolve\""));
            assertTrue(lines[0].contains("\"phase\":\"config read\""));
            assertTrue(lines[0].contains("\"projectRoot\":\"" + jsonPath(projectDir.toAbsolutePath().normalize()) + "\""));
            assertTrue(lines[1].contains("\"phase\":\"resolve graph\""));
            assertTrue(lines[1].contains("\"durationNanos\":"));
            assertTrue(lines[1].contains("\"conflicts\":\"0\""));
            assertTrue(lines[1].contains("\"downloadedArtifacts\":\"2\""));
            assertTrue(lines[1].contains("\"resolvedPackages\":\"1\""));
            assertTrue(lines[1].contains("\"pomCacheMisses\""));
            assertTrue(lines[1].contains("\"rawPomCacheHits\""));
            assertTrue(lines[1].contains("\"pomDownloadMillis\""));
            assertTrue(lines[1].contains("\"jarDownloadMillis\""));
            assertTrue(lines[1].contains("\"pomDownloadNanos\""));
            assertTrue(lines[1].contains("\"jarDownloadNanos\""));
            assertTrue(lines[1].contains("\"rawPomParseNanos\""));
            assertTrue(lines[1].contains("\"effectivePomBuildNanos\""));
            assertTrue(lines[1].contains("\"graphTraversalNanos\""));
            assertTrue(lines[1].contains("\"lockfileAssemblyNanos\""));
            assertTrue(lines[1].contains("\"lockfileWriteNanos\""));
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

    private static void writeProjectConfig(Path projectDir, Map<String, String> dependencies) throws IOException {
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2", dependencies);
    }

    private static void writeProjectConfig(
            Path projectDir,
            String repositoryUrl,
            Map<String, String> dependencies) throws IOException {
        Files.createDirectories(projectDir);
        StringBuilder config = new StringBuilder(memberConfig("demo") + """
                main = "com.example.Main"

                [repositories]
                test = "%s"

                [dependencies]
                """.formatted(repositoryUrl));
        dependencies.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> config.append('"')
                        .append(entry.getKey())
                        .append("\" = \"")
                        .append(entry.getValue())
                        .append("\"\n"));
        config.append("""

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);
        Files.writeString(projectDir.resolve("zolt.toml"), config.toString());
    }

    private static String jsonPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
