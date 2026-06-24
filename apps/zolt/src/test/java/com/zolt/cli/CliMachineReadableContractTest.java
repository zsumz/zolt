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

final class CliMachineReadableContractTest {
    @TempDir
    private Path tempDir;

    @Test
    void planJsonKeepsStableCompatibilityEnvelope() throws IOException {
        Path projectDir = tempDir.resolve("plan-contract");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("plan-contract"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute("plan", "--format", "json", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().startsWith("{\n  \"schemaVersion\": 1,"));
        assertTrue(result.stdout().contains("\"projectRoot\": \"" + projectDir.toAbsolutePath().normalize()));
        assertTrue(result.stdout().contains("\"project\": \"plan-contract\""));
        assertTrue(result.stdout().contains("\"target\": \"package\""));
        assertTrue(result.stdout().contains("\"status\": \"ready\""));
        assertTrue(result.stdout().contains("\"nodes\": ["));
        assertTrue(result.stdout().contains("\"id\": \"lockfile\""));
        assertTrue(result.stdout().contains("\"blockers\": []"));
        assertFalse(result.stdout().contains("Zolt plan"));
    }

    @Test
    void treeJsonKeepsStableCompatibilityEnvelope() throws IOException {
        Path projectDir = tempDir.resolve("tree-contract");
        writeProjectConfig(projectDir);
        writeLockfile(projectDir);

        CommandResult result = execute("tree", "--format", "json", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().startsWith("{\n  \"schemaVersion\": 1,"));
        assertTrue(result.stdout().contains("\"command\": \"tree\""));
        assertTrue(result.stdout().contains("\"coordinate\": \"com.example:demo:0.1.0\""));
        assertTrue(result.stdout().contains("\"packages\": ["));
        assertTrue(result.stdout().contains("\"coordinate\": \"com.example:app:1.0.0\""));
        assertTrue(result.stdout().contains("\"roots\": [\"com.example:app:1.0.0\"]"));
        assertTrue(result.stdout().contains("\"policyEffects\": []"));
        assertFalse(result.stdout().contains("\\- com.example:app"));
    }

    @Test
    void representativeSadPathKeepsExitCodeLocationAndRemediation() throws IOException {
        Path projectDir = tempDir.resolve("missing-config");
        Files.createDirectories(projectDir);

        CommandResult result = execute("build", "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.toml at "
                + projectDir.resolve("zolt.toml")));
        assertTrue(result.stderr().contains("Check that the file exists and is readable."));
        assertEquals("", result.stdout());
    }

    private static void writeProjectConfig(Path projectDir) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo") + """

                [repositories]
                test = "https://repo.maven.apache.org/maven2"
                """);
    }

    private static void writeLockfile(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:app"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                dependencies = []
                """);
    }
}
