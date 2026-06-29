package com.zolt.cli.resolve;

import com.zolt.cli.CliTestRepository;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.resolve.ResolveCommandTestSupport.jsonPath;
import static com.zolt.cli.resolve.ResolveCommandTestSupport.writeProjectConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResolveCommandTimingsTest {
    @TempDir
    private Path tempDir;

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
}
