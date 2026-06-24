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

final class NativeCommandWorkspaceTest {
    @TempDir
    private Path tempDir;

    @Test
    void nativeBuildsSelectedWorkspaceMemberFromCli() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace-native");
        NativeCommandTestSupport.writeWorkspaceNativeFixture(workspaceDir);
        Path nativeImage = NativeCommandTestSupport.writeFakeNativeImage(tempDir.resolve("native-image"));

        CommandResult result = execute(
                "native",
                "--workspace",
                "--member", "apps/api",
                "--directory", workspaceDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--native-image", nativeImage.toString());

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("Resolved workspace dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("Built native binary at "
                + workspaceDir.resolve("apps/api/target/native/api")
                + " in apps/api"));
        assertTrue(result.stdout().contains("Built native binaries for 1 workspace members"));
        assertTrue(Files.exists(workspaceDir.resolve("apps/api/target/native/api")));
        assertFalse(Files.exists(workspaceDir.resolve("modules/core/target/native/core")));
    }

    @Test
    void nativeWorkspaceRejectsAllWithExplicitMemberSelection() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace-native-selection");
        NativeCommandTestSupport.writeWorkspaceNativeFixture(workspaceDir);

        CommandResult result = execute(
                "native",
                "--workspace",
                "--all",
                "--member", "apps/api",
                "--cwd", workspaceDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Use either --all or member selection for workspace selection, not both."));
    }
}
