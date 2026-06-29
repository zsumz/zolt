package com.zolt.cli.workspace;

import com.zolt.cli.WorkspaceCommandFixture;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import com.zolt.cli.WorkspaceCommandFixture.WorkspaceApplicationFixture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspacePackageCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void packageWorkspaceMemberPackagesSelectedMemberOnly() throws IOException {
        WorkspaceApplicationFixture fixture = WorkspaceCommandFixture.create(tempDir, "workspace-package-member");

        CommandResult result = execute(
                "package",
                "--workspace",
                "--member", "apps/api",
                "--timings",
                "--timings-format", "json",
                "--cwd", fixture.apiDir().toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Resolved workspace dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("Packaged 1 compiled files as thin jar in apps/api"));
        assertTrue(result.stdout().contains("Included Main-Class manifest entry in apps/api"));
        assertTrue(result.stdout().contains("Packaged 1 workspace members"));
        String[] lines = result.stderr().lines().toArray(String[]::new);
        assertEquals(4, lines.length);
        assertTrue(lines[0].contains("\"phase\":\"plan workspace packages\""));
        assertTrue(lines[0].contains("\"depth\":1"));
        assertTrue(lines[0].contains("\"includedMembers\":\"2\""));
        assertTrue(lines[0].contains("\"selectedMembers\":\"1\""));
        assertTrue(lines[0].contains("\"resolvedLockfile\":\"true\""));
        assertTrue(lines[1].contains("\"phase\":\"build workspace package inputs\""));
        assertTrue(lines[1].contains("\"depth\":1"));
        assertTrue(lines[1].contains("\"members\":\"2\""));
        assertTrue(lines[1].contains("\"sourceFiles\":\"2\""));
        assertTrue(lines[1].contains("\"mainCompilationsSkipped\":\"0\""));
        assertTrue(lines[1].contains("\"mainCompilationsExecuted\":\"2\""));
        assertTrue(lines[2].contains("\"phase\":\"assemble workspace packages\""));
        assertTrue(lines[2].contains("\"depth\":1"));
        assertTrue(lines[2].contains("\"members\":\"1\""));
        assertTrue(lines[2].contains("\"entries\":\"1\""));
        assertTrue(lines[3].contains("\"phase\":\"package workspace\""));
        assertTrue(lines[3].contains("\"depth\":0"));
        assertTrue(lines[3].contains("\"members\":\"1\""));
        assertTrue(lines[3].contains("\"entries\":\"1\""));
        assertTrue(Files.exists(fixture.apiDir().resolve("target/api-0.1.0.jar")));
        assertFalse(Files.exists(fixture.coreDir().resolve("target/core-0.1.0.jar")));
    }
}
