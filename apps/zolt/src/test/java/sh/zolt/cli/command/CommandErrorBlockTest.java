package sh.zolt.cli.command;

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
    void keepsRawJavacDiagnosticsOutOfCoordinateRow() {
        CommandErrorBlock block = CommandErrorBlock.of(
                """
                javac failed with exit code 1.
                warning: [options] source value 8 is obsolete and will be removed in a future release
                To suppress warnings about obsolete options, use -Xlint:-options.
                src/main/java/demo/Bad.java:5: error: incompatible types: String cannot be converted to int
                module-info.java:29: error: package demo is not visible""",
                "Fix the Java compilation errors and try again.");

        assertTrue(
                block.contextRows().stream().noneMatch(row -> "Coordinate".equals(row.label())),
                "raw javac diagnostics must not produce a Coordinate row");
        assertEquals(
                "Fix the Java compilation errors and try again.", block.next().orElseThrow());
    }

    @Test
    void keepsResolverCoordinateRowForRealDependencyCoordinate() {
        CommandErrorBlock block = CommandErrorBlock.of(
                "Offline mode requires cached POM for com.example:missing:1.0.0 at /tmp/cache.",
                "Run the command without --offline to fetch it.");

        assertEquals(1, block.contextRows().size());
        assertEquals("Coordinate", block.contextRows().getFirst().label());
        assertEquals("com.example:missing:1.0.0", block.contextRows().getFirst().value());
        assertEquals(
                "Run the command without --offline to fetch it.", block.next().orElseThrow());
    }

    @Test
    void keepsFieldSectionAndUnsupportedRowsForStructuredMessages() {
        assertTrue(
                CommandErrorBlock.of("Unknown field [bogus] in zolt.toml.", "").contextRows().stream()
                        .anyMatch(row -> "Field".equals(row.label()) && "[bogus]".equals(row.value())),
                "Field row must survive");
        assertTrue(
                CommandErrorBlock.of("Unknown top-level section [nope] in zolt.toml.", "").contextRows().stream()
                        .anyMatch(row -> "Section".equals(row.label()) && "[nope]".equals(row.value())),
                "Section row must survive");
        assertTrue(
                CommandErrorBlock.of("Unsupported `java = \"99\"` value.", "").contextRows().stream()
                        .anyMatch(row -> "Unsupported".equals(row.label())),
                "Unsupported row must survive");
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
