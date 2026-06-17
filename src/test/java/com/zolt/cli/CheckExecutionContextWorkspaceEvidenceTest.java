package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CheckExecutionContextWorkspaceEvidenceTest {
    @TempDir
    private Path tempDir;

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
}
