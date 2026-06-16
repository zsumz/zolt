package com.zolt.cli;

import static com.zolt.cli.AddCommandNoResolveTestSupport.occurrences;
import static com.zolt.cli.AddCommandNoResolveTestSupport.writeProjectConfig;
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

final class AddCommandNoResolveTest {
    @TempDir
    private Path tempDir;

    @Test
    void addAddsCompileDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "com.google.guava:guava:33.4.0-jre");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Added dependency com.google.guava:guava:33.4.0-jre to [dependencies]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("\"com.google.guava:guava\" = \"33.4.0-jre\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsTestDependencyWithoutDuplicatingExistingEntry() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult first = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "test",
                "org.junit.jupiter:junit-jupiter:5.11.4");
        CommandResult second = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "test",
                "org.junit.jupiter:junit-jupiter:5.11.4");

        assertEquals(0, first.exitCode());
        assertEquals(0, second.exitCode());
        assertTrue(first.stdout().contains("Added dependency org.junit.jupiter:junit-jupiter:5.11.4 to [test.dependencies]"));
        assertTrue(second.stdout().contains("already exists in [test.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertEquals(1, occurrences(config, "\"org.junit.jupiter:junit-jupiter\" = \"5.11.4\""));
    }

    @Test
    void addAddsManagedDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "com.example:legacy-api");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency com.example:legacy-api with a platform-managed version to [dependencies]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("\"com.example:legacy-api\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsVersionRefDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo") + """

                [repositories]
                "central" = "https://repo.maven.apache.org/maven2"

                [versions]
                guava = "33.4.8-jre"
                """);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--version-ref",
                "guava",
                "com.google.guava:guava");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency com.google.guava:guava with versionRef `guava` = 33.4.8-jre to [dependencies]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[versions]\n\"guava\" = \"33.4.8-jre\""));
        assertTrue(config.contains("\"com.google.guava:guava\" = { versionRef = \"guava\" }"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsApiDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo") + """

                [repositories]
                "central" = "https://repo.maven.apache.org/maven2"

                [platforms]

                [dependencies]
                "com.example:contract" = "1.0.0"

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "api",
                "com.example:contract:2.0.0");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Updated dependency com.example:contract from 1.0.0 to 2.0.0 in [api.dependencies]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[api.dependencies]\n\"com.example:contract\" = \"2.0.0\""));
        assertFalse(config.contains("[dependencies]\n\"com.example:contract\" = \"1.0.0\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsManagedApiDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "api",
                "com.example:contract");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency com.example:contract with a platform-managed version to [api.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[api.dependencies]\n\"com.example:contract\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsRuntimeDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "runtime",
                "com.h2database:h2");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency com.h2database:h2 with a platform-managed version to [runtime.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[runtime.dependencies]\n\"com.h2database:h2\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsProvidedDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "provided",
                "jakarta.servlet:jakarta.servlet-api:6.1.0");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency jakarta.servlet:jakarta.servlet-api:6.1.0 to [provided.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[provided.dependencies]\n\"jakarta.servlet:jakarta.servlet-api\" = \"6.1.0\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsDevDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "dev",
                "org.springframework.boot:spring-boot-devtools");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency org.springframework.boot:spring-boot-devtools with a platform-managed version to [dev.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[dev.dependencies]\n\"org.springframework.boot:spring-boot-devtools\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

}
