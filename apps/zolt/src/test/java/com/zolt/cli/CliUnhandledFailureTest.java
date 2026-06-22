package com.zolt.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;

final class CliUnhandledFailureTest {
    @Test
    void uncaughtRuntimeFailuresPrintUserFacingError() {
        CommandLine commandLine = ZoltCli.newCommandLine();
        commandLine.addSubcommand("boom", new BoomCommand());
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        commandLine.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));

        int exitCode = commandLine.execute("boom");

        assertEquals(1, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("error: boom detail"));
    }

    @Command(name = "boom")
    private static final class BoomCommand implements Runnable {
        @Override
        public void run() {
            throw new IllegalStateException("boom detail");
        }
    }
}
