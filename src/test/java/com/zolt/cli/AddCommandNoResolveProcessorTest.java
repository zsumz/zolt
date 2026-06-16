package com.zolt.cli;

import static com.zolt.cli.AddCommandNoResolveTestSupport.writeProjectConfig;
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

final class AddCommandNoResolveProcessorTest {
    @TempDir
    private Path tempDir;

    @Test
    void addAddsProcessorDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "processor",
                "org.mapstruct:mapstruct-processor:1.6.3");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency org.mapstruct:mapstruct-processor:1.6.3 to [annotationProcessors]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[annotationProcessors]"));
        assertTrue(config.contains("\"org.mapstruct:mapstruct-processor\" = \"1.6.3\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsManagedTestProcessorDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "test-processor",
                "io.micronaut:micronaut-inject-java");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency io.micronaut:micronaut-inject-java with a platform-managed version to [test.annotationProcessors]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[test.annotationProcessors]"));
        assertTrue(config.contains("\"io.micronaut:micronaut-inject-java\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }
}
