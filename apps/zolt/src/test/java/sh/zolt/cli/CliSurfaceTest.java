package sh.zolt.cli;

import static sh.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CliSurfaceTest {
    @TempDir
    private Path tempDir;

    @Test
    void updateIsNotAPublicAlphaInstallPathByDefault() {
        CommandResult result = execute("update");

        assertEquals(1, result.exitCode());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().contains("zolt update is not a public-alpha install path"));
        assertTrue(result.stderr().contains("Download the native archive"));
    }

    @Test
    void updateDisabledErrorsUseModernHumanOutputControls() {
        CommandResult color = execute("--color", "always", "update");
        CommandResult quiet = execute("--quiet", "update");

        assertEquals(1, color.exitCode());
        assertTrue(color.stderr().contains("\u001B[31merror:\u001B[0m zolt update is not a public-alpha install path"));
        assertEquals("", color.stdout());
        assertFalse(color.stderr().contains("\u001B[31mNext"));
        assertEquals(1, quiet.exitCode());
        assertEquals("", quiet.stdout());
        assertTrue(quiet.stderr().contains("zolt update is not a public-alpha install path"));
    }

    @Test
    void rootHelpDoesNotAdvertiseUpdateForPublicAlpha() {
        CommandResult result = execute("--list");

        assertEquals(0, result.exitCode());
        assertFalse(result.stdout().contains("update"));
        assertTrue(result.stdout().contains("version"));
    }

    @Test
    void colorAlwaysDoesNotColorJsonOutput() throws Exception {
        Files.writeString(tempDir.resolve("zolt.toml"), CliTestSupport.memberConfig("json-output"));

        CommandResult result = execute(
                "--color=always",
                "plan",
                "--cwd", tempDir.toString(),
                "--format", "json");

        assertEquals(1, result.exitCode());
        assertFalse(result.stdout().contains("\u001B["));
        assertTrue(result.stdout().contains("\"target\": \"package\""));
        assertTrue(result.stdout().contains("\"status\": \"blocked\""));
    }

    @Test
    void invalidColorModeFailsBeforeCommandExecution() {
        CommandResult result = execute("--color", "rainbow", "help");

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("Invalid value for option '--color'"));
        assertTrue(result.stderr().contains("expected one of: auto, always, never"));
    }

    @Test
    void progressOptionsAreGlobalAndValidationMatchesColorMode() {
        CommandResult forced = execute("--progress", "always", "help");
        CommandResult disabled = execute("--no-progress", "help");
        CommandResult quiet = execute("--quiet", "help");
        CommandResult invalid = execute("--progress", "sparkles", "help");

        assertEquals(0, forced.exitCode());
        assertEquals("", forced.stderr());
        assertTrue(forced.stdout().contains("The modern Java build toolkit."));
        assertEquals(0, disabled.exitCode());
        assertEquals("", disabled.stderr());
        assertTrue(disabled.stdout().contains("The modern Java build toolkit."));
        assertEquals(0, quiet.exitCode());
        assertEquals("", quiet.stderr());
        assertTrue(quiet.stdout().contains("The modern Java build toolkit."));
        assertEquals(2, invalid.exitCode());
        assertTrue(invalid.stderr().contains("Invalid value for option '--progress'"));
        assertTrue(invalid.stderr().contains("expected one of: auto, always, never"));
    }

    @Test
    void quietKeepsFailuresActionable() {
        CommandResult result = execute("--quiet", "resolve", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().contains("error: Could not read zolt.toml"));
        assertTrue(result.stderr().contains("File: " + tempDir.resolve("zolt.toml")));
        assertTrue(result.stderr().contains("Next: Check that the file exists"));
    }

    @Test
    void initCreatesProjectAndPrintsNextCommand() {
        CommandResult result = execute("init", "--directory", tempDir.toString(), "hello");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Created Zolt project at"));
        assertTrue(result.stdout().contains("→ cd hello"));
        assertTrue(Files.exists(tempDir.resolve("hello/zolt.toml")));
    }

    @Test
    void initCreatesWorkspaceAndDefaultAppMember() {
        CommandResult result = execute("init", "--workspace", "--directory", tempDir.toString(), "platform");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Created Zolt workspace at"));
        assertTrue(result.stdout().contains("→ cd platform"));
        assertTrue(Files.exists(tempDir.resolve("platform/zolt.toml")));
        assertTrue(Files.exists(tempDir.resolve("platform/apps/platform/zolt.toml")));
        assertTrue(Files.exists(tempDir.resolve("platform/apps/platform/src/main/java/com/example/Main.java")));
    }

    @Test
    void initUsesModernHumanOutputControls() {
        CommandResult color = execute("--color=always", "init", "--directory", tempDir.toString(), "color-hello");
        CommandResult quiet = execute("--quiet", "init", "--directory", tempDir.toString(), "quiet-hello");

        String createdLead = "\u001B[32m✔\u001B[0m";
        String pointerArrow = "\u001B[36m→\u001B[0m";
        String pointerPath = "\u001B[36mcolor-hello\u001B[0m";
        assertEquals(0, color.exitCode());
        assertTrue(color.stdout().contains(createdLead + " Created Zolt project at"));
        assertTrue(color.stdout().contains("  " + pointerArrow + " cd " + pointerPath));
        assertFalse(color.stdout()
                .replace(createdLead, "✔")
                .replace(pointerArrow, "→")
                .replace(pointerPath, "color-hello")
                .contains("\u001B["));
        assertEquals(0, quiet.exitCode());
        assertEquals("", quiet.stdout());
        assertEquals("", quiet.stderr());
        assertTrue(Files.exists(tempDir.resolve("color-hello/zolt.toml")));
        assertTrue(Files.exists(tempDir.resolve("quiet-hello/zolt.toml")));
    }

    @Test
    void packageReportsConfigErrorsCleanly() {
        CommandResult result = execute("package", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.toml"));
    }

    @Test
    void resolveReportsConfigErrorsCleanly() {
        CommandResult result = execute("resolve", "--cwd", tempDir.toString(), "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.toml"));
        assertEquals(1, occurrences(result.stderr(), "error: Could not read zolt.toml"));
        assertTrue(result.stderr().contains("File: " + tempDir.resolve("zolt.toml")));
        assertTrue(result.stderr().contains("Next: Check that the file exists"));
    }

    @Test
    void failedCommandStillPrintsTimingsWhenRequested() {
        CommandResult result = execute("resolve", "--timings", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.toml"));
        assertTrue(result.stderr().contains("Timings for zolt resolve"));
        assertTrue(result.stderr().contains("config read:"));
        assertTrue(result.stderr().contains("status=failed"));
    }

    @Test
    void registersMvpCommandSurface() {
        Set<String> subcommands = ZoltCli.newCommandLine().getSubcommands().keySet();

        assertTrue(subcommands.containsAll(Set.of(
                "help",
                "init",
                "version",
                "update",
                "config",
                "check",
                "add",
                "remove",
                "platform",
                "resolve",
                "tree",
                "why",
                "policy",
                "conflicts",
                "explain",
                "plan",
                "classpath",
                "ide",
                "quarkus",
                "aliases",
                "tasks",
                "task",
                "build",
                "run",
                "test",
                "integration-test",
                "coverage",
                "package",
                "publish",
                "run-package",
                "native",
                "native-smoke",
                "release-archive",
                "release-verify",
                "self-check",
                "self-parity",
                "clean",
                "doctor")));
        assertEquals(commandClass("classpath"), "sh.zolt.cli.command.resolve.ClasspathCommand");
        assertEquals(commandClass("config"), "sh.zolt.cli.command.config.ConfigCommand");
        assertEquals(commandClass("version"), "sh.zolt.cli.command.dependency.VersionCommand");
        assertEquals(commandClass("update"), "sh.zolt.cli.command.update.UpdateCommand");
        assertEquals(commandClass("native-smoke"), "sh.zolt.cli.command.nativeimage.NativeSmokeCommand");
        assertEquals(commandClass("aliases"), "sh.zolt.cli.command.task.AliasesCommand");
        assertEquals(commandClass("tasks"), "sh.zolt.cli.command.task.TasksCommand");
        assertEquals(commandClass("task"), "sh.zolt.cli.command.task.TaskCommand");
        assertEquals(commandClass("release-verify"), "sh.zolt.cli.command.publish.ReleaseVerifyCommand");
        assertEquals(commandClass("release-archive"), "sh.zolt.cli.command.publish.ReleaseArchiveCommand");
        assertEquals(commandClass("publish"), "sh.zolt.cli.command.publish.PublishCommand");
        assertEquals(commandClass("native"), "sh.zolt.cli.command.nativeimage.NativeCommand");
        assertEquals(commandClass("plan"), "sh.zolt.cli.command.build.PlanCommand");
        assertEquals(commandClass("explain"), "sh.zolt.cli.command.insight.ExplainCommand");
    }

    private static String commandClass(String command) {
        return ZoltCli.newCommandLine()
                .getSubcommands()
                .get(command)
                .getCommandSpec()
                .userObject()
                .getClass()
                .getName();
    }

    private static int occurrences(String text, String fragment) {
        int count = 0;
        int start = 0;
        while (true) {
            int index = text.indexOf(fragment, start);
            if (index < 0) {
                return count;
            }
            count++;
            start = index + fragment.length();
        }
    }
}
