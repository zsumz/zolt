package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RemoveCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void removeMissingDependencyPrintsFriendlyNoOpMessage() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult result = execute(
                "remove",
                "--cwd", projectDir.toString(),
                "com.example:missing");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Dependency com.example:missing is not present in [dependencies]; nothing to remove."));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void removeWithoutSectionDoesNotDeleteApiDependency() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeApiDependencyProject(projectDir);

        CommandResult result = execute(
                "remove",
                "--cwd", projectDir.toString(),
                "com.example:contract");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Dependency com.example:contract is not present in [dependencies]; nothing to remove."));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[api.dependencies]\n\"com.example:contract\" = \"1.0.0\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void removeDeletesApiDependency() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeApiDependencyProject(projectDir);

        CommandResult result = execute(
                "remove",
                "--cwd", projectDir.toString(),
                "api",
                "com.example:contract");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Removed dependency com.example:contract from [api.dependencies]"));
        assertTrue(result.stdout().contains("Resolved 0 packages"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertFalse(config.contains("\"com.example:contract\" = \"1.0.0\""));
    }

    private static void writeProjectConfig(Path projectDir) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo") + """
                main = "com.example.Main"

                [repositories]
                test = "https://repo.maven.apache.org/maven2"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);
    }

    private static void writeApiDependencyProject(Path projectDir) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo") + """

                [repositories]
                "central" = "https://repo.maven.apache.org/maven2"

                [platforms]

                [api.dependencies]
                "com.example:contract" = "1.0.0"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);
    }
}
