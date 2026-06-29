package com.zolt.cli.dependency;

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

final class AddCommandValidationTest {
    @TempDir
    private Path tempDir;

    @Test
    void addRejectsManagedDependencyWithExplicitVersion() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--managed",
                "com.example:legacy-api:1.0.0");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Managed dependency coordinate must not include a version."));
        assertTrue(result.stderr().contains("Next: Use `group:artifact`."));
    }

    @Test
    void addRejectsUnsupportedLiteralDependencyVersion() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "com.example:legacy-api:1.0-SNAPSHOT");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains(
                "Invalid external dependency version `1.0-SNAPSHOT` for dependency. SNAPSHOT versions are not supported in this context"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertFalse(config.contains("legacy-api"));
    }

    @Test
    void addRejectsUnknownVersionRef() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--version-ref",
                "guava",
                "com.google.guava:guava");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Unknown versionRef `guava`."));
        assertTrue(result.stderr().contains("Next: Add [versions].guava or use an explicit version."));
    }

    @Test
    void addRejectsVersionRefWithManagedOrExplicitVersion() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult managedResult = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--managed",
                "--version-ref",
                "guava",
                "com.google.guava:guava");
        CommandResult explicitVersionResult = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--version-ref",
                "guava",
                "com.google.guava:guava:33.4.8-jre");

        assertEquals(1, managedResult.exitCode());
        assertTrue(managedResult.stderr().contains("`--managed` and `--version-ref` cannot be used together"));
        assertEquals(1, explicitVersionResult.exitCode());
        assertTrue(explicitVersionResult.stderr().contains(
                "Version-ref dependency coordinate must not include a version."));
        assertTrue(explicitVersionResult.stderr().contains(
                "Next: Use `--version-ref guava group:artifact`."));
    }

    @Test
    void addRejectsUnknownDependencySectionWithSupportedSections() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "compile-only",
                "com.example:tool:1.0.0");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Unexpected dependency section `compile-only`"));
        assertTrue(result.stderr().contains("zolt add api group:artifact"));
        assertTrue(result.stderr().contains("zolt add runtime group:artifact"));
        assertTrue(result.stderr().contains("zolt add provided group:artifact"));
        assertTrue(result.stderr().contains("zolt add dev group:artifact"));
        assertTrue(result.stderr().contains("zolt add processor group:artifact"));
        assertTrue(result.stderr().contains("zolt add test-processor group:artifact"));
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
}
