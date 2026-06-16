package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.IdeModelCommandTestSupport.writeProjectConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IdeModelCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void ideModelPrintsNestedJsonTimingsWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("ide-timings");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "ide", "model",
                "--format", "json",
                "--timings",
                "--timings-format", "json",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"schemaVersion\": 1"));
        assertTrue(result.stderr().contains("\"phase\":\"read ide project config\""));
        assertTrue(result.stderr().contains("\"phase\":\"build ide classpaths\""));
        assertTrue(result.stderr().contains("\"phase\":\"build ide framework model\""));
        assertTrue(result.stderr().contains("\"phase\":\"assemble ide model\""));
        assertTrue(result.stderr().contains("\"phase\":\"ide model export\""));
        assertTrue(result.stderr().contains("\"phase\":\"ide model json\""));
        assertTrue(result.stderr().contains("\"depth\":1"));
        assertTrue(result.stderr().contains("\"testClasspathEntries\""));
    }

}
