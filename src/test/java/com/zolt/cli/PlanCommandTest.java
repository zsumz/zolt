package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.generatedSourceConfig;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PlanCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void planReportsTypedPipelineAndBlockersWithoutExecutingWork() throws IOException {
        Path projectDir = tempDir.resolve("plan-blocked");
        Files.createDirectories(projectDir.resolve("src/main/openapi"));
        Files.writeString(projectDir.resolve("src/main/openapi/api.yaml"), "openapi: 3.1.0\n");
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("plan-blocked")
                + generatedSourceConfig("main", "openapi", "target/generated/sources/openapi", "src/main/openapi/api.yaml", true)
                + """

                [resources.filtering]
                enabled = true
                includes = ["**/*.properties"]
                missing = "fail"

                [resources.tokens]
                projectVersion = { project = "version" }

                [package]
                mode = "spring-boot-war"
                """);

        CommandResult result = execute("plan", "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("Zolt plan"));
        assertTrue(result.stdout().contains("Target: package"));
        assertTrue(result.stdout().contains("Status: blocked"));
        assertTrue(result.stdout().contains("- lockfile [resolve] blocked"));
        assertTrue(result.stdout().contains("blocker missing-lockfile: zolt.lock is missing"));
        assertTrue(result.stdout().contains("- generate-main-openapi [generated-source] blocked"));
        assertTrue(result.stdout().contains("blocker missing-generated-source-output"));
        assertTrue(result.stdout().contains("- process-main-resources [resources] ready"));
        assertTrue(result.stdout().contains("tokens: [projectVersion]"));
        assertTrue(result.stdout().contains("- assemble-package [package] blocked"));
        assertTrue(result.stdout().contains("blocker missing-main-class"));
        assertEquals("", result.stderr());
    }

    @Test
    void planJsonRedactsTestEnvironmentValues() throws IOException {
        Path projectDir = tempDir.resolve("plan-json");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("plan-json") + """

                [test.runtime]
                jvmArgs = ["--add-opens=java.base/java.lang=ALL-UNNAMED"]
                systemProperties = { "logs.dir" = "${project.root}/test-logs" }
                environment = { TZ = "America/Chicago" }
                events = ["failed", "skipped"]
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "plan",
                "--target", "test",
                "--reports-dir", "target/test-reports",
                "--format", "json",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"target\": \"test\""));
        assertTrue(result.stdout().contains("\"id\": \"run-tests\""));
        assertTrue(result.stdout().contains("\"target/test-reports\""));
        assertTrue(result.stdout().contains("\"environment: [TZ] (values redacted)\""));
        assertFalse(result.stdout().contains("America/Chicago"));
        assertEquals("", result.stderr());
    }

    @Test
    void planRejectsUnsafeReportsDirectory() throws IOException {
        Path projectDir = tempDir.resolve("plan-unsafe-reports");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("plan-unsafe-reports"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "plan",
                "--target", "test",
                "--reports-dir", "../reports",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Invalid --reports-dir path `../reports` resolved to "));
        assertTrue(result.stderr().contains("Use a project-relative path under "
                + projectDir.toAbsolutePath().normalize()));
    }

    @Test
    void planCiIncludesExplicitCoverageAndPublishNodes() throws IOException {
        Path projectDir = tempDir.resolve("plan-ci");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("plan-ci"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute("plan", "--target", "ci", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("- coverage [coverage] planned"));
        assertTrue(result.stdout().contains("command: zolt coverage"));
        assertTrue(result.stdout().contains("- publish-dry-run [publish] planned"));
        assertTrue(result.stdout().contains("mode: dry-run"));
    }

    @Test
    void planShowsTypedOpenApiGenerationEvidenceWithoutExecutingIt() throws IOException {
        Path projectDir = tempDir.resolve("plan-openapi-generated-source");
        Files.createDirectories(projectDir.resolve("src/main/openapi"));
        Files.writeString(projectDir.resolve("src/main/openapi/public-api.yaml"), "openapi: 3.1.0\n");
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("plan-openapi-generated-source") + """

                [versions]
                openapi = "7.11.0"

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                versionRef = "openapi"

                [generated.openapiPresets.spring-api]
                generator = "spring"
                library = "spring-boot"
                options = { interfaceOnly = "true" }

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                preset = "spring-api"
                options = { hideGenerationTimestamp = "true" }
                """);

        CommandResult result = execute("plan", "--target", "build", "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("- generate-main-public-api [generated-source] ready"));
        assertTrue(result.stdout().contains("kind: openapi"));
        assertTrue(result.stdout().contains("ownership: zolt-owned-openapi"));
        assertTrue(result.stdout().contains("toolArtifact: org.openapitools:openapi-generator-cli:7.11.0"));
        assertTrue(result.stdout().contains("toolVersionRef: openapi"));
        assertTrue(result.stdout().contains("toolFingerprint: "));
        assertTrue(result.stdout().contains("optionsFingerprint: "));
        assertEquals("", result.stderr());
    }

    @Test
    void planReportsStaleGeneratedSourceOutputs() throws IOException {
        Path projectDir = tempDir.resolve("plan-stale-generated-source");
        Path input = projectDir.resolve("src/main/openapi/api.yaml");
        Path output = projectDir.resolve("target/generated/sources/openapi/com/example/GeneratedApi.java");
        Files.createDirectories(input.getParent());
        Files.createDirectories(output.getParent());
        Files.writeString(input, "openapi: 3.1.0\n");
        Files.writeString(output, "package com.example; public final class GeneratedApi {}\n");
        Files.setLastModifiedTime(output, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(input, FileTime.fromMillis(2_000));
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("plan-stale-generated-source")
                + generatedSourceConfig("main", "openapi", "target/generated/sources/openapi", "src/main/openapi/api.yaml", true));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute("plan", "--target", "build", "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("freshness: stale"));
        assertTrue(result.stdout().contains("blocker stale-generated-source-output"));
        assertTrue(result.stdout().contains("Required generated source output `target/generated/sources/openapi` is older"));
    }
}
