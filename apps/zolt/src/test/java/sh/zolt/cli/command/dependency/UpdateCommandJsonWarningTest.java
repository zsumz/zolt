package sh.zolt.cli.command.dependency;

import static sh.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.maven.metadata.MetadataDiscovery;
import sh.zolt.maven.metadata.VersionDiscovery;
import sh.zolt.toml.ZoltTomlParser;
import sh.zolt.toml.ZoltTomlWriter;
import sh.zolt.update.UpdateEngine;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * The JSON output mode must honour the same comment-rewrite contract as text mode: before rewriting a
 * commented zolt.toml it warns, but on STDERR so STDOUT stays valid machine-readable JSON.
 */
final class UpdateCommandJsonWarningTest {
    private static final String WARNING =
            "Warning: zolt.toml contains comments; this edit rewrites the file and may remove comments or formatting.";

    @TempDir
    private Path tempDir;

    @Test
    void jsonModeWarnsOnStderrAndKeepsStdoutValidJsonWhenRewritingCommentedConfig() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Path configPath = projectDir.resolve("zolt.toml");
        Files.writeString(configPath, memberConfig("demo") + """

                [repositories]
                central = "http://127.0.0.1:1/maven2"

                # pin lib for reproducibility
                [dependencies]
                "com.example:lib" = "1.0.0"

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);

        Result result = runUpdateJson(projectDir, discovery("com.example", "lib", "1.0.0", "1.1.0"));

        assertEquals(0, result.exitCode(), result.stderr());
        // The warning goes to STDERR, never STDOUT.
        assertTrue(result.stderr().contains(WARNING), result.stderr());
        assertFalse(result.stdout().contains("Warning"), result.stdout());
        assertFalse(result.stdout().contains("comments"), result.stdout());
        // STDOUT is the plan JSON and nothing else.
        String stdout = result.stdout().strip();
        assertTrue(stdout.startsWith("{"), stdout);
        assertTrue(stdout.endsWith("}"), stdout);
        assertTrue(stdout.contains("\"command\": \"update\""), stdout);
        assertTrue(stdout.contains("\"from\": \"1.0.0\""), stdout);
        assertTrue(stdout.contains("\"to\": \"1.1.0\""), stdout);
        // The file was rewritten: version bumped, comment dropped (the very risk the warning names).
        String rewritten = Files.readString(configPath);
        assertTrue(rewritten.contains("\"com.example:lib\" = \"1.1.0\""), rewritten);
        assertFalse(rewritten.contains("# pin lib for reproducibility"), rewritten);
    }

    @Test
    void jsonDryRunNeitherWarnsNorRewrites() throws IOException {
        Path projectDir = tempDir.resolve("dry");
        Files.createDirectories(projectDir);
        Path configPath = projectDir.resolve("zolt.toml");
        String original = memberConfig("dry") + """

                [repositories]
                central = "http://127.0.0.1:1/maven2"

                # keep this comment
                [dependencies]
                "com.example:lib" = "1.0.0"

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """;
        Files.writeString(configPath, original);

        Result result = runUpdateJsonDryRun(projectDir, discovery("com.example", "lib", "1.0.0", "1.1.0"));

        assertEquals(0, result.exitCode(), result.stderr());
        assertFalse(result.stderr().contains(WARNING), result.stderr());
        assertTrue(result.stdout().strip().startsWith("{"), result.stdout());
        assertEquals(original, Files.readString(configPath));
    }

    private Result runUpdateJson(Path projectDir, VersionDiscovery discovery) {
        return run(projectDir, discovery, "--format", "json", "--no-resolve", "--directory", projectDir.toString());
    }

    private Result runUpdateJsonDryRun(Path projectDir, VersionDiscovery discovery) {
        return run(
                projectDir,
                discovery,
                "--format", "json", "--dry-run", "--no-resolve", "--directory", projectDir.toString());
    }

    private Result run(Path projectDir, VersionDiscovery discovery, String... args) {
        // resolveService is never touched because every invocation passes --no-resolve.
        UpdateCommand command =
                new UpdateCommand(new ZoltTomlParser(), new ZoltTomlWriter(), null, new UpdateEngine(discovery));
        // Match ZoltCli.newCommandLine() so `--format json` (lowercase) parses like the real CLI.
        CommandLine commandLine = new CommandLine(command).setCaseInsensitiveEnumValuesAllowed(true);
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        commandLine.setOut(new PrintWriter(stdout));
        commandLine.setErr(new PrintWriter(stderr));
        int exitCode = commandLine.execute(args);
        return new Result(exitCode, stdout.toString(), stderr.toString());
    }

    private static VersionDiscovery discovery(String groupId, String artifactId, String... versions) {
        Map<String, String> sourceByVersion = new java.util.LinkedHashMap<>();
        for (String version : versions) {
            sourceByVersion.putIfAbsent(version, "central");
        }
        MetadataDiscovery listing = new MetadataDiscovery(true, List.of(versions), sourceByVersion, List.of());
        MetadataDiscovery missing = new MetadataDiscovery(false, List.of(), Map.of(), List.of());
        return (repositories, group, artifact, offline) ->
                group.equals(groupId) && artifact.equals(artifactId) ? listing : missing;
    }

    private record Result(int exitCode, String stdout, String stderr) {
    }
}
