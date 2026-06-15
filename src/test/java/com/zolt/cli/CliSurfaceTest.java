package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CliSurfaceTest {
    @Test
    void updateExplainsFutureSelfUpdatePath() {
        CommandResult result = execute("update");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("zolt update is not available yet."));
        assertTrue(result.stdout().contains("verified native archive"));
        assertTrue(result.stdout().contains("followUps/-design-zolt-update-command.md"));
        assertEquals("", result.stderr());
    }

    @Test
    void helpListsMvpCommands() {
        CommandResult result = execute("help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("The modern Java build toolkit."));
        assertTrue(result.stdout().contains("init"));
        assertTrue(result.stdout().contains("resolve"));
        assertTrue(result.stdout().contains("check"));
        assertTrue(result.stdout().contains("build"));
        assertTrue(result.stdout().contains("doctor"));
    }

    @Test
    void registersMvpCommandSurface() {
        Set<String> subcommands = ZoltCli.newCommandLine().getSubcommands().keySet();

        assertTrue(subcommands.containsAll(Set.of(
                "help",
                "init",
                "version",
                "update",
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
                "build",
                "run",
                "test",
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
        assertEquals(commandClass("classpath"), "com.zolt.cli.command.ClasspathCommand");
        assertEquals(commandClass("version"), "com.zolt.cli.command.VersionCommand");
        assertEquals(commandClass("update"), "com.zolt.cli.command.UpdateCommand");
        assertEquals(commandClass("native-smoke"), "com.zolt.cli.command.NativeSmokeCommand");
        assertEquals(commandClass("release-verify"), "com.zolt.cli.command.ReleaseVerifyCommand");
        assertEquals(commandClass("release-archive"), "com.zolt.cli.command.ReleaseArchiveCommand");
        assertEquals(commandClass("publish"), "com.zolt.cli.command.PublishCommand");
        assertEquals(commandClass("native"), "com.zolt.cli.command.NativeCommand");
        assertEquals(commandClass("plan"), "com.zolt.cli.command.PlanCommand");
        assertEquals(commandClass("explain"), "com.zolt.cli.command.ExplainCommand");
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

}
