package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CliProjectDirectoryGuardrailTest {
    private static final Path COMMAND_SOURCE_ROOT = RepositoryPaths.appRoot()
            .resolve("src/main/java/com/zolt/cli/command");
    private static final String SHARED_PROJECT_DIRECTORY =
            "apps/zolt/src/main/java/com/zolt/cli/command/CommandProjectDirectory.java";
    private static final Pattern DIRECT_CWD_OPTION = Pattern.compile(
            "@Option\\s*\\([^)]*names\\s*=\\s*\"--cwd\"",
            Pattern.DOTALL);

    @Test
    void cliCommandsDeclareCwdOnlyThroughProjectDirectoryMixin() throws IOException {
        Set<String> directCwdOptionFiles = directCwdOptionFiles(COMMAND_SOURCE_ROOT);

        assertEquals(
                Set.of(SHARED_PROJECT_DIRECTORY),
                directCwdOptionFiles,
                () -> "CLI commands should use CommandProjectDirectory instead of declaring direct --cwd options.");
    }

    @Test
    void scannerFindsDirectCwdOptions(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java/com/example");
        write(sourceRoot.resolve("SharedProjectDirectory.java"), """
                import picocli.CommandLine.Option;

                final class SharedProjectDirectory {
                    @Option(names = "--cwd", hidden = true)
                    private java.nio.file.Path workingDirectory;
                }
                """);
        write(sourceRoot.resolve("BuildCommand.java"), """
                import picocli.CommandLine.Option;

                final class BuildCommand {
                    @Option(
                            names = "--cwd",
                            hidden = true)
                    private java.nio.file.Path workingDirectory;
                }
                """);
        write(sourceRoot.resolve("RunCommand.java"), """
                import picocli.CommandLine.Mixin;

                final class RunCommand {
                    @Mixin
                    private SharedProjectDirectory projectDirectory;
                }
                """);

        assertEquals(
                Set.of(
                        RepositoryPaths.displayPath(sourceRoot.resolve("BuildCommand.java")),
                        RepositoryPaths.displayPath(sourceRoot.resolve("SharedProjectDirectory.java"))),
                directCwdOptionFiles(sourceRoot));
    }

    private static Set<String> directCwdOptionFiles(Path sourceRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            return Set.of();
        }
        Set<String> files = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path javaFile : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                if (DIRECT_CWD_OPTION.matcher(Files.readString(javaFile)).find()) {
                    files.add(RepositoryPaths.displayPath(javaFile));
                }
            }
        }
        return files;
    }

    private static void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
