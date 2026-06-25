package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DoctorCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void doctorReportsJdkStatus() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult result = execute("--color=always", "doctor", "--directory", projectDir.toString());
        CommandResult quiet = execute("--quiet", "doctor", "--directory", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("JDK status: \u001B[32mok\u001B[0m"));
        assertTrue(result.stdout().contains("java: "));
        assertTrue(result.stdout().contains("javac: "));
        assertTrue(result.stdout().contains("jar: "));
        assertTrue(result.stdout().contains("version: " + currentJavaMajorVersion()));
        assertEquals(0, quiet.exitCode(), quiet.stderr());
        assertEquals("", quiet.stdout());
    }

    @Test
    void doctorStylesJdkProblemErrorPrefixWhenColorIsForced() throws IOException {
        Path projectDir = tempDir.resolve("version-mismatch");
        writeProjectConfig(projectDir, "999");

        CommandResult result = execute("--color=always", "doctor", "--directory", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("\u001B[31merror:\u001B[0m Java version mismatch."));
        assertTrue(result.stderr().contains("\u001B[31merror:\u001B[0m Project health check failed."));
    }

    @Test
    void doctorReportsSelfHostingReadiness() throws IOException {
        Path projectDir = tempDir.resolve("self-hosting-ready");
        writeSelfHostingProjectConfig(projectDir, true);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        Files.createDirectories(projectDir.resolve("src/main/java"));
        Files.createDirectories(projectDir.resolve("src/test/java"));

        CommandResult result = execute("--color=always", "doctor", "--self-hosting", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Self-hosting status: \u001B[32mok\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[32mok:\u001B[0m main class - project main is com.example.Main"));
        assertTrue(result.stdout().contains("\u001B[32mok:\u001B[0m JUnit Platform Console"));
        assertTrue(result.stdout().contains("\u001B[32mok:\u001B[0m native no-fallback"));
        assertFalse(result.stdout().contains("\u001B[32mok: main class"));
    }

    @Test
    void doctorReportsSelfHostingGapsWithNextSteps() throws IOException {
        Path projectDir = tempDir.resolve("self-hosting-gaps");
        writeSelfHostingProjectConfig(projectDir, false);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        Files.createDirectories(projectDir.resolve("src/main/java"));
        Files.createDirectories(projectDir.resolve("src/test/java"));

        CommandResult result = execute("--color=always", "doctor", "--self-hosting", "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("Self-hosting status: \u001B[31merror\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[31merror:\u001B[0m JUnit Platform Console - add org.junit.platform:junit-platform-console-standalone to [test.dependencies]"));
        assertFalse(result.stdout().contains("\u001B[31merror: JUnit Platform Console"));
    }

    private static void writeProjectConfig(Path projectDir) throws IOException {
        writeProjectConfig(projectDir, currentJavaMajorVersion());
    }

    private static void writeProjectConfig(Path projectDir, String javaVersion) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
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
                """.formatted(javaVersion));
    }

    private static void writeSelfHostingProjectConfig(Path projectDir, boolean includeTestRunner) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                main = "com.example.Main"

                [repositories]
                central = "https://repo.maven.apache.org/maven2"

                %s
                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"

                [native]
                imageName = "demo"
                output = "target/native"
                args = ["--no-fallback"]
                """.formatted(
                currentJavaMajorVersion(),
                includeTestRunner
                        ? """
                        [test.dependencies]
                        "org.junit.platform:junit-platform-console-standalone" = "1.11.4"

                        """
                        : ""));
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }
}
