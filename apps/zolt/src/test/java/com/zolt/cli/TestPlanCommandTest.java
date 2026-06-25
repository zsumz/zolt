package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestPlanCommandTest extends TestCommandTestSupport {
    @TempDir
    private Path tempDir;

    @Test
    void testPlanReportsSuiteMembershipAndFilters() throws IOException {
        Path projectDir = tempDir.resolve("plan-demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        appendSuite(projectDir, """

                [test.suites.fast]
                includeClassname = ["*Test"]
                excludeClassname = ["*ContractTest"]
                includeTag = ["fast"]
                excludeTag = ["slow"]
                """);
        writeClass(projectDir, "target/test-classes/com/example/FastServiceTest.class");
        writeClass(projectDir, "target/test-classes/com/example/UserContractTest.class");

        CommandResult result = execute(
                "test",
                "plan",
                "--cwd", projectDir.toString(),
                "--suite", "fast");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Test plan for demo"));
        assertTrue(result.stdout().contains("suite: fast"));
        assertTrue(result.stdout().contains("configured suite: yes"));
        assertTrue(result.stdout().contains("matched entries: 1"));
        assertTrue(result.stdout().contains("empty: no"));
        assertTrue(result.stdout().contains("class filters: include *Test; exclude *ContractTest"));
        assertTrue(result.stdout().contains("tag filters: include fast; exclude slow"));
        assertTrue(result.stdout().contains("entries: 1"));
        assertTrue(result.stdout().contains("- com.example.FastServiceTest"));
        assertFalse(Files.exists(projectDir.resolve("target/test-reports")));
        assertFalse(Files.exists(projectDir.resolve("target/coverage")));
    }

    @Test
    void testPlanShowsEmptySuiteClearly() throws IOException {
        Path projectDir = tempDir.resolve("empty-demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        appendSuite(projectDir, """

                [test.suites.fast]
                includeClassname = ["*MissingTest"]
                """);
        writeClass(projectDir, "target/test-classes/com/example/FastServiceTest.class");

        CommandResult result = execute(
                "test",
                "plan",
                "--cwd", projectDir.toString(),
                "--suite", "fast");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("suite: fast"));
        assertTrue(result.stdout().contains("matched entries: 0"));
        assertTrue(result.stdout().contains("empty: yes"));
    }

    @Test
    void testPlanReportsOverlappingSuiteMembership() throws IOException {
        Path projectDir = tempDir.resolve("overlap-demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        appendSuite(projectDir, """

                [test.suites.fast]
                includeClassname = ["*Test"]

                [test.suites.contract]
                includeClassname = ["*ContractTest"]
                """);
        writeClass(projectDir, "target/test-classes/com/example/UserContractTest.class");

        CommandResult result = execute(
                "test",
                "plan",
                "--cwd", projectDir.toString(),
                "--suite", "fast");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("matched entries: 1"));
        assertTrue(result.stdout().contains("overlapping entries: 1"));
        assertTrue(result.stdout().contains("- com.example.UserContractTest also matches contract"));
    }

    @Test
    void testPlanRejectsUnknownSuite() throws IOException {
        Path projectDir = tempDir.resolve("unknown-demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "test",
                "plan",
                "--cwd", projectDir.toString(),
                "--suite", "missing");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Unknown test suite `missing`"));
        assertTrue(result.stderr().contains("Add [test.suites.missing] to zolt.toml"));
    }

    @Test
    void testPlanShowsShardPlanWithoutWritingManifests() throws IOException {
        Path projectDir = tempDir.resolve("shard-plan-demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        appendSuite(projectDir, """

                [test.suites.fast]
                includeClassname = ["*Test"]
                """);
        writeClass(projectDir, "target/test-classes/com/example/AlphaTest.class");
        writeClass(projectDir, "target/test-classes/com/example/BetaTest.class");
        writeClass(projectDir, "target/test-classes/com/example/GammaTest.class");

        CommandResult result = execute(
                "test",
                "plan",
                "--cwd", projectDir.toString(),
                "--suite", "fast",
                "--shard-count", "2");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("shards: 2"));
        assertTrue(result.stdout().contains("- shard 1/2: 2 entries, empty: no, manifest: target/test-shards/fast/shard-1-of-2.json"));
        assertTrue(result.stdout().contains("- shard 2/2: 1 entries, empty: no, manifest: target/test-shards/fast/shard-2-of-2.json"));
        assertFalse(Files.exists(projectDir.resolve("target/test-shards")));
    }

    private static void appendSuite(Path projectDir, String config) throws IOException {
        Files.writeString(projectDir.resolve("zolt.toml"), config, StandardOpenOption.APPEND);
    }

    private static void writeClass(Path projectDir, String relativePath) throws IOException {
        Path classFile = projectDir.resolve(relativePath);
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, new byte[] {0});
    }
}
