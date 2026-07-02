package sh.zolt.cli.task;

import static sh.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import sh.zolt.cli.CliTestSupport;
import sh.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TaskCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void listsConfiguredTasks() throws IOException {
        Files.writeString(tempDir.resolve("zolt.toml"), CliTestSupport.memberConfig("task-list") + """

                [commands.tasks.fmt]
                description = "Format Java sources"
                cmd = ["scripts/format"]

                [commands.tasks.docs]
                cmd = ["python3", "-m", "http.server"]
                """);

        CommandResult result = execute("tasks", "--cwd", tempDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Tasks:"));
        assertTrue(result.stdout().contains("fmt"));
        assertTrue(result.stdout().contains("Format Java sources"));
        assertTrue(result.stdout().contains("docs"));
        assertEquals("", result.stderr());
    }

    @Test
    void runsTaskWithConfiguredEnvironmentCwdAndPassthroughArguments() throws IOException {
        Path script = writeScript("scripts/echo-task.sh", """
                printf 'cwd=%s\\n' "$PWD"
                printf 'env=%s\\n' "$APP_ENV"
                printf 'args=%s\\n' "$*"
                """);
        Files.createDirectories(tempDir.resolve("work"));
        Files.writeString(tempDir.resolve("zolt.toml"), CliTestSupport.memberConfig("task-run") + """

                [commands.tasks.echo]
                cmd = ["sh", "../scripts/echo-task.sh"]
                cwd = "work"
                env = { APP_ENV = "local" }
                """);

        CommandResult result = execute(
                "task",
                "--cwd", tempDir.toString(),
                "echo",
                "--",
                "--flag",
                "value");

        assertTrue(Files.exists(script));
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("cwd=" + tempDir.resolve("work").toRealPath()));
        assertTrue(result.stdout().contains("env=local"));
        assertTrue(result.stdout().contains("args=--flag value"));
        assertEquals("", result.stderr());
    }

    @Test
    void propagatesStartedTaskExitCodeAndStreamsChildOutput() throws IOException {
        writeScript("fail.sh", """
                printf 'child stdout\\n'
                printf 'child stderr\\n' >&2
                exit 7
                """);
        Files.writeString(tempDir.resolve("zolt.toml"), CliTestSupport.memberConfig("task-fail") + """

                [commands.tasks.fail]
                cmd = ["sh", "fail.sh"]
                """);

        CommandResult result = execute("task", "--cwd", tempDir.toString(), "fail");

        assertEquals(7, result.exitCode());
        assertTrue(result.stdout().contains("child stdout"));
        assertTrue(result.stderr().contains("child stderr"));
        assertTrue(result.stderr().contains("error: Task `fail` exited with code 7."));
    }

    @Test
    void reportsUnknownTasksWithAvailableNames() throws IOException {
        Files.writeString(tempDir.resolve("zolt.toml"), CliTestSupport.memberConfig("task-missing") + """

                [commands.tasks.fmt]
                cmd = ["scripts/format"]
                """);

        CommandResult result = execute("task", "--cwd", tempDir.toString(), "docs");

        assertEquals(1, result.exitCode());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().contains("error: Unknown task `docs`"));
        assertTrue(result.stderr().contains("Available tasks: fmt."));
        assertTrue(result.stderr().contains("Run `zolt tasks`"));
    }

    @Test
    void rejectsTaskCwdThatEscapesThroughSymlink() throws IOException {
        Path outside = Files.createDirectories(tempDir.resolveSibling(tempDir.getFileName() + "-outside"));
        Path link = tempDir.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "symbolic links are not available");
        }
        Files.writeString(tempDir.resolve("zolt.toml"), CliTestSupport.memberConfig("task-cwd") + """

                [commands.tasks.escape]
                cmd = ["sh", "-c", "printf should-not-run"]
                cwd = "link"
                """);

        CommandResult result = execute("task", "--cwd", tempDir.toString(), "escape");

        assertEquals(1, result.exitCode());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().contains("error: Invalid cwd for task `escape`"));
        assertTrue(result.stderr().contains("Task working directories must stay under"));
    }

    @Test
    void readsWorkspaceRootTasksWhenStartedFromMemberDirectory() throws IOException {
        Path member = tempDir.resolve("apps/api");
        Files.createDirectories(member);
        Files.writeString(tempDir.resolve("zolt.toml"), """
                [workspace]
                name = "tasks-workspace"
                members = ["apps/api"]

                [commands.tasks.root-task]
                description = "Root task"
                cmd = ["scripts/root-task"]
                """);
        Files.writeString(member.resolve("zolt.toml"), CliTestSupport.memberConfig("api") + """

                [commands.tasks.member-task]
                description = "Member task"
                cmd = ["scripts/member-task"]
                """);

        CommandResult result = execute("tasks", "--cwd", member.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("root-task"));
        assertTrue(result.stdout().contains("Root task"));
        assertFalse(result.stdout().contains("member-task"));
    }

    private Path writeScript(String relativePath, String body) throws IOException {
        Path script = tempDir.resolve(relativePath);
        Files.createDirectories(script.getParent());
        Files.writeString(script, "#!/usr/bin/env sh\n" + body);
        script.toFile().setExecutable(true);
        return script;
    }
}
