package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CheckCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void checkSucceedsForTypedProjectModel() throws IOException {
        Path projectDir = tempDir.resolve("check-demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-demo"));

        CommandResult result = execute("check", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals(
                "ok command-surface check-demo zolt check uses typed Zolt project data; no Maven, Gradle, or shell hooks are run.\n",
                result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void checkJsonOutputUsesStableResultShape() throws IOException {
        Path projectDir = tempDir.resolve("check-json");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-json"));

        CommandResult result = execute("check", "--format", "json", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().startsWith("{\"status\":\"ok\",\"projectRoot\":\""));
        assertTrue(result.stdout().contains("\"workspace\":false"));
        assertTrue(result.stdout().contains("\"id\":\"command-surface\""));
        assertTrue(result.stdout().contains("\"severity\":\"info\""));
        assertTrue(result.stdout().contains("\"status\":\"passed\""));
        assertTrue(result.stdout().contains("\"subject\":\"check-json\""));
        assertTrue(result.stdout().endsWith("]}\n"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkRefusesArbitraryHookNames() throws IOException {
        Path projectDir = tempDir.resolve("check-hook");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-hook"));

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "mvn verify");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error unsupported-check mvn verify Unsupported quality check `mvn verify`."));
        assertTrue(result.stdout().contains("Zolt does not run Maven goals, Gradle tasks, shell commands, or arbitrary hooks."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkReportsMalformedConfigAsFailedCheck() throws IOException {
        Path projectDir = tempDir.resolve("check-malformed");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-malformed") + """

                [check]
                command = "mvn verify"
                """);

        CommandResult result = execute("check", "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error command-surface zolt.toml Unknown top-level section [check] in zolt.toml."));
        assertTrue(result.stdout().contains("next: Fix zolt.toml, then run `zolt check` again."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkWorkspaceUsesMemberSelectionModel() throws IOException {
        WorkspaceCommandFixture.WorkspaceApplicationFixture fixture =
                WorkspaceCommandFixture.create(tempDir, "check-workspace");

        CommandResult result = execute(
                "check",
                "--workspace",
                "--member", "apps/api",
                "--cwd", fixture.workspaceDir().toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok command-surface check-workspace zolt check selected 2 workspace members"));
        assertTrue(result.stdout().contains("no Maven, Gradle, or shell hooks are run."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkPrintsTimingsWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("check-timings");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-timings"));

        CommandResult result = execute("check", "--timings", "--timings-format", "json", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok command-surface check-timings"));
        assertTrue(result.stderr().contains("\"command\":\"check\""));
        assertTrue(result.stderr().contains("\"phase\":\"run quality checks\""));
        assertTrue(result.stderr().contains("\"checks\":\"1\""));
        assertTrue(result.stderr().contains("\"passed\":\"1\""));
    }

    @Test
    void checkCacheIntegrityReportsCorruptedLockedArtifact() throws IOException {
        Path projectDir = tempDir.resolve("check-cache-integrity");
        Path cacheRoot = tempDir.resolve("cache-integrity-cache");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-cache-integrity"));
        Path jar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "corrupted runtime jar bytes");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = true
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                jarSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
                dependencies = []
                """);

        CommandResult result = execute(
                "check",
                "--check", "cache-integrity",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains(
                "error cache-integrity zolt.lock Cached jar integrity check failed for com.example:runtime-lib:1.0.0"));
        assertTrue(result.stdout().contains("next: Remove the cache entry or run `zolt resolve`."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkCacheIntegrityMalformedLockfileUsesLockfileRemediation() throws IOException {
        Path projectDir = tempDir.resolve("check-cache-integrity-malformed-lockfile");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-cache-integrity-malformed-lockfile"));
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = 42
                """);

        CommandResult result = execute(
                "check",
                "--check", "cache-integrity",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error cache-integrity zolt.lock Invalid value type in zolt.lock"));
        assertTrue(result.stdout().contains("next: Run `zolt resolve` to regenerate zolt.lock."));
        assertFalse(result.stdout().contains("Remove the cache entry"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkWorkspaceCacheIntegrityMissingLockfileUsesWorkspaceRemediation() throws IOException {
        Path workspaceDir = tempDir.resolve("check-workspace-cache-integrity-missing-lockfile");
        Path apiDir = workspaceDir.resolve("apps/api");
        Files.createDirectories(apiDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "check-workspace-cache-integrity-missing-lockfile"
                members = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));

        CommandResult result = execute(
                "check",
                "--workspace",
                "--member", "apps/api",
                "--check", "cache-integrity",
                "--cwd", workspaceDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error cache-integrity zolt.lock zolt.lock is missing."));
        assertTrue(result.stdout().contains("next: Run `zolt resolve --workspace`."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkWorkspaceCacheIntegrityMalformedLockfileUsesWorkspaceRemediation() throws IOException {
        Path workspaceDir = tempDir.resolve("check-workspace-cache-integrity-malformed-lockfile");
        Path apiDir = workspaceDir.resolve("apps/api");
        Files.createDirectories(apiDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "check-workspace-cache-integrity-malformed-lockfile"
                members = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));
        Files.writeString(workspaceDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = 42
                """);

        CommandResult result = execute(
                "check",
                "--workspace",
                "--member", "apps/api",
                "--check", "cache-integrity",
                "--cwd", workspaceDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error cache-integrity zolt.lock Invalid value type in zolt.lock"));
        assertTrue(result.stdout().contains("next: Run `zolt resolve --workspace` to regenerate zolt.lock."));
        assertFalse(result.stdout().contains("Remove the cache entry"));
        assertEquals("", result.stderr());
    }
}
