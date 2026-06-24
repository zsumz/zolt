package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CliOutputGuardrailTest {
    private static final Path CLI_SOURCE_ROOT = RepositoryPaths.appRoot()
            .resolve("src/main/java/com/zolt/cli");
    private static final Path CLI_COMMAND_SOURCE_ROOT = CLI_SOURCE_ROOT.resolve("command");
    private static final Pattern DIRECT_ERROR_WRITE = Pattern.compile(
            "(?:print|println)\\s*\\(\\s*\"error:",
            Pattern.DOTALL);
    private static final Pattern DIRECT_HUMAN_STDOUT_WRITE = Pattern.compile(
            "(?:spec\\.commandLine\\(\\)\\.getOut\\(\\)"
                    + "|commandLine\\.getOut\\(\\)"
                    + "|\\bout)\\s*\\.\\s*(?:print|println|printf)\\s*\\(",
            Pattern.DOTALL);
    private static final Set<String> RAW_STDOUT_EXEMPTIONS = Set.of(
            "CommandOutput.java",
            "VersionCommand.java");

    @Test
    void productionCliSourcesUseSharedErrorOutputHelpers() throws IOException {
        assertEquals(
                Set.of(),
                directErrorWriteFiles(CLI_SOURCE_ROOT),
                () -> "CLI commands should route user-facing errors through CommandFailures "
                        + "or CommandHumanOutput.errors so color, quiet, and duplicate handling stay consistent.");
    }

    @Test
    void productionCliCommandsUseSharedHumanOutputHelpers() throws IOException {
        assertEquals(
                Set.of(),
                directHumanStdoutWriteFiles(CLI_COMMAND_SOURCE_ROOT),
                () -> "CLI commands should route human summaries through CommandHumanOutput "
                        + "or raw machine/process output through CommandOutput.");
    }

    @Test
    void scannerFindsDirectErrorWrites(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java/com/example");
        write(sourceRoot.resolve("DirectErrorCommand.java"), """
                final class DirectErrorCommand {
                    void run(java.io.PrintWriter err) {
                        err.println("error: direct message");
                    }
                }
                """);
        write(sourceRoot.resolve("SharedErrorCommand.java"), """
                final class SharedErrorCommand {
                    void run(com.zolt.cli.CommandHumanOutput output) {
                        output.error("shared message");
                    }
                }
                """);

        assertEquals(
                Set.of(RepositoryPaths.displayPath(sourceRoot.resolve("DirectErrorCommand.java"))),
                directErrorWriteFiles(sourceRoot));
    }

    @Test
    void scannerFindsDirectHumanStdoutWrites(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java/com/example");
        write(sourceRoot.resolve("DirectSummaryCommand.java"), """
                final class DirectSummaryCommand {
                    private picocli.CommandLine.Model.CommandSpec spec;

                    void run() {
                        spec.commandLine().getOut().println("Built project");
                    }
                }
                """);
        write(sourceRoot.resolve("SharedSummaryCommand.java"), """
                final class SharedSummaryCommand {
                    void run(com.zolt.cli.CommandHumanOutput output) {
                        output.success("Built project");
                    }
                }
                """);

        assertEquals(
                Set.of(RepositoryPaths.displayPath(sourceRoot.resolve("DirectSummaryCommand.java"))),
                directHumanStdoutWriteFiles(sourceRoot));
    }

    private static Set<String> directErrorWriteFiles(Path sourceRoot) throws IOException {
        Set<String> files = new TreeSet<>();
        for (Path javaFile : ArchitectureSourceFiles.javaFiles(List.of(sourceRoot))) {
            if (DIRECT_ERROR_WRITE.matcher(Files.readString(javaFile)).find()) {
                files.add(RepositoryPaths.displayPath(javaFile));
            }
        }
        return files;
    }

    private static Set<String> directHumanStdoutWriteFiles(Path sourceRoot) throws IOException {
        Set<String> files = new TreeSet<>();
        for (Path javaFile : ArchitectureSourceFiles.javaFiles(List.of(sourceRoot))) {
            if (RAW_STDOUT_EXEMPTIONS.contains(javaFile.getFileName().toString())) {
                continue;
            }
            if (DIRECT_HUMAN_STDOUT_WRITE.matcher(Files.readString(javaFile)).find()) {
                files.add(RepositoryPaths.displayPath(javaFile));
            }
        }
        return files;
    }

    private static void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
