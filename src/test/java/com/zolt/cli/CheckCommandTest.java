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

final class CheckCommandTest extends CheckCommandTestSupport {

    @Test
    void checkSucceedsForTypedProjectModel() throws IOException {
        Path projectDir = createProject("check-demo");

        CommandResult result = execute("check", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals(
                "ok command-surface check-demo zolt check uses typed Zolt project data; no Maven, Gradle, or shell hooks are run.\n",
                result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void checkJsonOutputUsesStableResultShape() throws IOException {
        Path projectDir = createProject("check-json");

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
        Path projectDir = createProject("check-hook");

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "mvn verify");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error unsupported-check mvn verify Unsupported quality check `mvn verify`."));
        assertTrue(result.stdout().contains("Zolt does not run Maven goals, Gradle tasks, shell commands, or arbitrary hooks."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkReportsMalformedConfigAsFailedCheck() throws IOException {
        Path projectDir = createProject("check-malformed");
        Files.writeString(projectDir.resolve("zolt.toml"), """

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

}
