package com.zolt.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CommandErrorBlockTest {
    @Test
    void extractsFileContextAndNextStep() {
        CommandErrorBlock block = CommandErrorBlock.from(
                "Could not read zolt.toml at /tmp/app/zolt.toml. Check that the file exists and is readable.");

        assertEquals("Could not read zolt.toml at /tmp/app/zolt.toml.", block.summary());
        assertEquals(1, block.contextRows().size());
        assertEquals("File", block.contextRows().getFirst().label());
        assertEquals("/tmp/app/zolt.toml", block.contextRows().getFirst().value());
        assertEquals("Check that the file exists and is readable.", block.next().orElseThrow());
    }

    @Test
    void structuredFactoryUsesRemediationVerbatimAndKeepsContextRows() {
        CommandErrorBlock block = CommandErrorBlock.of(
                "Could not read zolt.toml at /tmp/app/zolt.toml.",
                "Add the file or pass --cwd to the project directory.");

        assertEquals("Could not read zolt.toml at /tmp/app/zolt.toml.", block.summary());
        assertEquals(1, block.contextRows().size());
        assertEquals("File", block.contextRows().getFirst().label());
        assertEquals("/tmp/app/zolt.toml", block.contextRows().getFirst().value());
        assertEquals("Add the file or pass --cwd to the project directory.", block.next().orElseThrow());
    }

    @Test
    void structuredFactoryMatchesHeuristicForMigratedReadFailure() {
        CommandErrorBlock structured = CommandErrorBlock.of(
                "Could not read zolt.toml at /tmp/app/zolt.toml.",
                "Check that the file exists and is readable.");
        CommandErrorBlock heuristic = CommandErrorBlock.from(
                "Could not read zolt.toml at /tmp/app/zolt.toml. Check that the file exists and is readable.");

        assertEquals(heuristic, structured);
    }

    @Test
    void keepsMultilineRunnerOutputOutOfContextRows() {
        CommandErrorBlock block = CommandErrorBlock.from("""
                java exited with code 1. Check the application output and try again.

                Failures (1):
                  JUnit Jupiter:CliSurfaceTest:resolveReportsConfigErrorsCleanly()
                    => org.opentest4j.AssertionFailedError: error: Could not read zolt.toml at /tmp/app/zolt.toml.

                File: /tmp/app/zolt.toml
                Next: Check that the file exists and is readable.
                Coordinate: Jupiter:CliSurfaceTest:resolveReportsConfigErrorsCleanly
                """);

        assertEquals("java exited with code 1.", block.summary());
        assertTrue(block.contextRows().isEmpty());
        String next = block.next().orElseThrow();
        assertTrue(next.startsWith("Check the application output and try again."));
        assertTrue(next.contains("Failures (1):"));
        assertTrue(next.contains("Coordinate: Jupiter:CliSurfaceTest:resolveReportsConfigErrorsCleanly"));
    }
}
