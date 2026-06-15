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

final class WorkspaceResolveCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void resolveWorkspaceWritesRootLockfile() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                defaultMembers = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));

        CommandResult result = execute(
                "resolve",
                "--workspace",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Resolved 0 packages"));
        assertTrue(result.stdout().contains("Wrote " + workspaceDir.resolve("zolt.lock")));
        assertTrue(Files.readString(workspaceDir.resolve("zolt.lock"))
                .contains("projectResolutionFingerprint = \"sha256:"));
        assertFalse(Files.exists(apiDir.resolve("zolt.lock")));
        assertFalse(Files.exists(coreDir.resolve("zolt.lock")));
    }
}
