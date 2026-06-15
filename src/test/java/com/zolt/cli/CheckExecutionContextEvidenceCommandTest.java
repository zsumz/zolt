package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static com.zolt.cli.CliTestSupport.sha256;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CheckExecutionContextEvidenceCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void checkContextCiRejectsMissingJUnitReportsWhenConfigured() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-missing-reports");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-context-ci-missing-reports"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--check", "execution-context",
                "--reports-dir", "target/test-reports",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error execution-context target/test-reports CI context expected JUnit XML reports, but the report directory is missing."));
        assertTrue(result.stdout().contains("next: Run `zolt test --reports-dir target/test-reports`"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkContextCiRejectsUnsafeReportsDirectory() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-unsafe-reports");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-context-ci-unsafe-reports"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--check", "execution-context",
                "--reports-dir", "../reports",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error execution-context --reports-dir Invalid --reports-dir path `../reports` resolved to "));
        assertTrue(result.stdout().contains("Use a project-relative path under "
                + projectDir.toAbsolutePath().normalize()));
        assertEquals("", result.stderr());
    }

    @Test
    void checkContextCiAcceptsJUnitReportsWhenConfigured() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-reports-ok");
        Path reportsDir = projectDir.resolve("target/test-reports");
        Files.createDirectories(reportsDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-context-ci-reports-ok"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        Files.writeString(reportsDir.resolve("TEST-demo.xml"), "<testsuite tests=\"1\" failures=\"0\"/>\n");

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--check", "execution-context",
                "--reports-dir", "target/test-reports",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok execution-context test-reports CI test report preflight found 1 JUnit XML report."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkContextCiRejectsMissingCoverageReportsWhenConfigured() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-missing-coverage");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-context-ci-missing-coverage"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--check", "execution-context",
                "--coverage-dir", "target/coverage",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error execution-context target/coverage CI context expected coverage reports, but the coverage directory is missing."));
        assertTrue(result.stdout().contains("next: Run `zolt coverage` so coverage evidence exists under target/coverage"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkContextCiRejectsUnsafeCoverageDirectory() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-unsafe-coverage");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-context-ci-unsafe-coverage"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--check", "execution-context",
                "--coverage-dir", "../coverage",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error execution-context --coverage-dir Invalid --coverage-dir path `../coverage` resolved to "));
        assertTrue(result.stdout().contains("Use a project-relative path under "
                + projectDir.toAbsolutePath().normalize()));
        assertEquals("", result.stderr());
    }

    @Test
    void checkContextCiAcceptsCoverageReportsWhenConfigured() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-coverage-ok");
        Path coverageDir = projectDir.resolve("target/coverage");
        Files.createDirectories(coverageDir.resolve("html"));
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-context-ci-coverage-ok"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        Files.writeString(coverageDir.resolve("jacoco.exec"), "exec\n");
        Files.writeString(coverageDir.resolve("jacoco.xml"), "<report name=\"demo\"/>\n");
        Files.writeString(coverageDir.resolve("html/index.html"), "<!doctype html>\n");

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--check", "execution-context",
                "--coverage-dir", "target/coverage",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok execution-context coverage-reports CI coverage preflight found Jacoco execution data, 1 XML report, and 1 HTML report."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkContextCiAcceptsWorkspaceJUnitReportsWhenConfigured() throws IOException {
        Path workspaceDir = tempDir.resolve("check-context-ci-workspace-reports-ok");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path reportsDir = apiDir.resolve("target/test-reports/apps/api");
        Files.createDirectories(reportsDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "check-context-ci-workspace-reports-ok"
                members = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));
        Files.writeString(workspaceDir.resolve("zolt.lock"), "version = 1\n");
        Files.writeString(reportsDir.resolve("TEST-api.xml"), "<testsuite tests=\"1\" failures=\"0\"/>\n");

        CommandResult result = execute(
                "check",
                "--workspace",
                "--member", "apps/api",
                "--context", "ci",
                "--check", "execution-context",
                "--reports-dir", "target/test-reports",
                "--cwd", workspaceDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok execution-context apps/api test-reports CI test report preflight found 1 JUnit XML report."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkContextCiAcceptsWorkspaceCoverageReportsWhenConfigured() throws IOException {
        Path workspaceDir = tempDir.resolve("check-context-ci-workspace-coverage-ok");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coverageDir = apiDir.resolve("target/coverage");
        Files.createDirectories(coverageDir.resolve("html"));
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "check-context-ci-workspace-coverage-ok"
                members = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));
        Files.writeString(workspaceDir.resolve("zolt.lock"), "version = 1\n");
        Files.writeString(coverageDir.resolve("jacoco.exec"), "exec\n");
        Files.writeString(coverageDir.resolve("jacoco.xml"), "<report name=\"api\"/>\n");
        Files.writeString(coverageDir.resolve("html/index.html"), "<!doctype html>\n");

        CommandResult result = execute(
                "check",
                "--workspace",
                "--member", "apps/api",
                "--context", "ci",
                "--check", "execution-context",
                "--coverage-dir", "target/coverage",
                "--cwd", workspaceDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok execution-context apps/api coverage-reports CI coverage preflight found Jacoco execution data, 1 XML report, and 1 HTML report."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkContextCiRejectsMissingWorkspaceCoverageReportsWhenConfigured() throws IOException {
        Path workspaceDir = tempDir.resolve("check-context-ci-workspace-coverage-missing");
        Path apiDir = workspaceDir.resolve("apps/api");
        Files.createDirectories(apiDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "check-context-ci-workspace-coverage-missing"
                members = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));
        Files.writeString(workspaceDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "check",
                "--workspace",
                "--member", "apps/api",
                "--context", "ci",
                "--check", "execution-context",
                "--coverage-dir", "target/coverage",
                "--cwd", workspaceDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error execution-context apps/api target/coverage CI context expected coverage reports, but the coverage directory is missing."));
        assertTrue(result.stdout().contains("next: Run `zolt coverage` from each selected member so coverage evidence exists under target/coverage"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkContextCiJsonOutputIsStable() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-json");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-context-ci-json"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--check", "execution-context",
                "--format", "json",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"id\":\"execution-context\""));
        assertTrue(result.stdout().contains("\"subject\":\"ci\""));
        assertTrue(result.stdout().contains("\"status\":\"passed\""));
        assertTrue(result.stdout().contains("CI context policy is active"));
        assertTrue(result.stdout().contains("Policy source: built-in ci context"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkContextCiAcceptsPublishDryRunPreflightWhenRequired() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-publish-dry-run-ok");
        Files.createDirectories(projectDir.resolve("target"));
        Path artifact = projectDir.resolve("target/check-context-ci-publish-dry-run-ok-0.1.0.jar");
        Path sourcesArtifact = projectDir.resolve("target/check-context-ci-publish-dry-run-ok-0.1.0-sources.jar");
        Files.writeString(artifact, "fake package\n");
        Files.writeString(sourcesArtifact, "fake sources\n");
        Files.writeString(projectDir.resolve("target/check-context-ci-publish-dry-run-ok-0.1.0.jar.zolt-package.json"), """
                {
                  "schema": "zolt.package-evidence.v1",
                  "archive": "target/check-context-ci-publish-dry-run-ok-0.1.0.jar",
                  "archiveSha256": "%s",
                  "artifacts": [
                    {
                      "classifier": "main",
                      "type": "thin",
                      "path": "target/check-context-ci-publish-dry-run-ok-0.1.0.jar",
                      "entries": 1,
                      "sha256": "%s"
                    },
                    {
                      "classifier": "sources",
                      "type": "jar",
                      "path": "target/check-context-ci-publish-dry-run-ok-0.1.0-sources.jar",
                      "entries": 1,
                      "sha256": "%s"
                    }
                  ]
                }
                """.formatted(sha256(artifact), sha256(artifact), sha256(sourcesArtifact)));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-context-ci-publish-dry-run-ok") + """

                [publish]
                releaseRepository = "company-releases"

                [publish.repositories.company-releases]
                url = "https://repo.example.test/releases"
                """);

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--check", "execution-context",
                "--require-publish-dry-run",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok execution-context publish-dry-run CI publish dry-run preflight is ready for com.example:check-context-ci-publish-dry-run-ok:0.1.0"));
        assertTrue(result.stdout().contains("with 2 artifacts and generated POM metadata."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkContextCiReportsPublishDryRunBlockersWhenRequired() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-publish-dry-run-blocked");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-context-ci-publish-dry-run-blocked") + """

                [publish]
                releaseRepository = "company-releases"

                [publish.repositories.company-releases]
                url = "https://repo.example.test/releases"
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--check", "execution-context",
                "--require-publish-dry-run",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error execution-context publish-dry-run CI publish dry-run blocker: missing artifact: run `zolt package`"));
        assertTrue(result.stdout().contains("next: Run `zolt publish --dry-run` and resolve the reported blocker before release CI."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkContextCiPublishDryRunJsonOutputIsStable() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-publish-dry-run-json");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-context-ci-publish-dry-run-json"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--check", "execution-context",
                "--require-publish-dry-run",
                "--format", "json",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("\"id\":\"execution-context\""));
        assertTrue(result.stdout().contains("\"subject\":\"publish-dry-run\""));
        assertTrue(result.stdout().contains("\"status\":\"failed\""));
        assertTrue(result.stdout().contains("CI publish dry-run preflight failed"));
        assertTrue(result.stdout().contains("No [publish] configuration found"));
        assertEquals("", result.stderr());
    }
}
