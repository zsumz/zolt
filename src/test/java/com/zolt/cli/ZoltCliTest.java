package com.zolt.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class ZoltCliTest {
    @Test
    void versionPrintsZoltVersion() {
        CommandResult result = execute("--version");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("zolt 0.1.0-SNAPSHOT"));
    }

    @Test
    void helpListsMvpCommands() {
        CommandResult result = execute("help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("init"));
        assertTrue(result.stdout().contains("resolve"));
        assertTrue(result.stdout().contains("build"));
        assertTrue(result.stdout().contains("doctor"));
    }

    @Test
    void registersMvpCommandSurface() {
        Set<String> subcommands = ZoltCli.newCommandLine().getSubcommands().keySet();

        assertTrue(subcommands.containsAll(Set.of(
                "help",
                "init",
                "add",
                "remove",
                "resolve",
                "tree",
                "why",
                "conflicts",
                "build",
                "run",
                "test",
                "package",
                "clean",
                "doctor")));
    }

    @Test
    void registeredCommandsPrintActionableStubMessage() {
        CommandResult result = execute("resolve");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("zolt resolve is not implemented yet."));
        assertTrue(result.stdout().contains("Next step: follow the matching followUp in followUps/."));
    }

    private static CommandResult execute(String... args) {
        CommandLine commandLine = ZoltCli.newCommandLine();
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        commandLine.setOut(new PrintWriter(stdout));
        commandLine.setErr(new PrintWriter(stderr));

        int exitCode = commandLine.execute(args);

        return new CommandResult(exitCode, stdout.toString(), stderr.toString());
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
