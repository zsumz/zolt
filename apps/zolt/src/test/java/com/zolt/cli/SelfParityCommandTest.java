package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SelfParityCommandTest extends PackageCommandTestSupport {
    @TempDir
    private Path tempDir;

    @Test
    void selfParityHelpShowsDirectoryOption() {
        CommandResult result = execute("self-parity", "--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("--directory"));
        assertTrue(result.stdout().contains("Run as if Zolt was started in the given project"));
        assertTrue(result.stdout().contains("directory."));
        assertEquals("", result.stderr());
    }

    @Test
    void selfParityReadsSelectedDirectory() {
        Path projectDir = tempDir.resolve("selected");

        CommandResult result = execute(
                "self-parity",
                "--directory", projectDir.toString(),
                "--bootstrap-jar", "build/bootstrap-zolt/zolt-bootstrap.jar",
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Self-hosting parity requires bootstrap jar"));
        assertTrue(result.stderr().contains(projectDir.resolve("build/bootstrap-zolt/zolt-bootstrap.jar").toString()));
    }

    @Test
    void selfParityKeepsHiddenCwdCompatibility() {
        Path projectDir = tempDir.resolve("hidden-cwd");

        CommandResult result = execute(
                "self-parity",
                "--cwd", projectDir.toString(),
                "--bootstrap-jar", "build/bootstrap-zolt/zolt-bootstrap.jar",
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains(projectDir.resolve("build/bootstrap-zolt/zolt-bootstrap.jar").toString()));
    }

    @Test
    void selfParityMismatchStylesErrorPrefixAndSuppressesDuplicateHandlerError() throws IOException {
        Path projectDir = tempDir.resolve("mismatch");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;
                public final class Main {
                    public static void main(String[] args) {}
                }
                """);
        writeBootstrapJar(projectDir.resolve("bootstrap.jar"));

        CommandResult result = execute(
                "--color=always",
                "self-parity",
                "--directory", projectDir.toString(),
                "--bootstrap-jar", "bootstrap.jar",
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("\u001B[31merror:\u001B[0m Self-hosting parity failed"));
        assertTrue(result.stderr().contains("Missing from Zolt-built jar:"));
        assertTrue(result.stderr().contains("only-bootstrap.txt"));
        assertEquals(1, occurrences(result.stderr(), "Self-hosting parity failed"));
    }

    private static void writeBootstrapJar(Path path) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(java.nio.file.Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("only-bootstrap.txt"));
            zip.write("bootstrap only\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        int index = text.indexOf(value);
        while (index >= 0) {
            count++;
            index = text.indexOf(value, index + value.length());
        }
        return count;
    }
}
