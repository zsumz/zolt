package com.zolt.workspace.discovery;

import static com.zolt.workspace.discovery.WorkspaceDiscoveryServiceTestSupport.createSymlink;
import static com.zolt.workspace.discovery.WorkspaceDiscoveryServiceTestSupport.member;
import static com.zolt.workspace.discovery.WorkspaceDiscoveryServiceTestSupport.rootWorkspace;
import static com.zolt.workspace.discovery.WorkspaceDiscoveryServiceTestSupport.workspace;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.workspace.WorkspaceConfigException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceDiscoveryValidationTest {
    private final WorkspaceDiscoveryService service = new WorkspaceDiscoveryService();

    @TempDir
    private Path tempDir;

    @Test
    void rejectsMemberPathsThatEscapeWorkspaceRoot() throws IOException {
        workspace(tempDir, """
                [workspace]
                name = "bad"
                members = ["../outside"]
                """);

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertTrue(exception.getMessage().contains("Invalid workspace member path `../outside` in [workspace].members."));
        assertTrue(exception.getMessage().contains("[workspace].members"));
        assertTrue(exception.getMessage().contains("resolved to"));
    }

    @Test
    void rejectsWindowsAbsoluteWorkspaceMemberPathsOnEveryHost() throws IOException {
        workspace(tempDir, """
                [workspace]
                name = "bad"
                members = ["C:\\\\outside\\\\member"]
                """);

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertTrue(exception.getMessage().contains("Invalid workspace member path `C:\\outside\\member`"));
        assertTrue(exception.getMessage().contains("[workspace].members"));
    }

    @Test
    void rejectsWorkspaceMemberSymlinkThatEscapesWorkspaceRoot() throws IOException {
        workspace(tempDir, """
                [workspace]
                name = "bad"
                members = ["apps/api"]
                """);
        Path outside = Files.createTempDirectory(tempDir.getParent(), "outside-member-");
        Files.writeString(outside.resolve("zolt.toml"), """
                [project]
                name = "api"
                version = "0.1.0"
                group = "com.acme"
                java = "21"
                """);
        createSymlink(tempDir.resolve("apps/api"), outside);

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertTrue(exception.getMessage().contains("[workspace].members"));
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
    }

    @Test
    void rejectsMissingMemberConfig() throws IOException {
        workspace(tempDir, """
                [workspace]
                name = "bad"
                members = ["apps/api"]
                """);
        Files.createDirectories(tempDir.resolve("apps/api"));

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertEquals(
                "Workspace member `apps/api` must contain zolt.toml at "
                        + tempDir.toAbsolutePath().normalize().resolve("apps/api/zolt.toml")
                        + ".",
                exception.getMessage());
    }

    @Test
    void rejectsAmbiguousWorkspaceConfigPaths() throws IOException {
        workspace(tempDir, """
                [workspace]
                name = "legacy"
                members = ["apps/api"]
                """);
        rootWorkspace(tempDir, """
                [workspace]
                name = "root"
                members = ["apps/api"]
                """);

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertEquals(
                "Ambiguous workspace config at "
                        + tempDir.toAbsolutePath().normalize()
                        + ". Use either zolt.toml with [workspace] or zolt-workspace.toml, not both.",
                exception.getMessage());
    }

    @Test
    void rejectsDuplicateMemberCoordinates() throws IOException {
        workspace(tempDir, """
                [workspace]
                name = "bad"
                members = ["apps/api", "modules/api-copy"]
                """);
        member(tempDir, "apps/api", "api", "com.acme", "");
        member(tempDir, "modules/api-copy", "api", "com.acme", "");

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertEquals(
                "Workspace member coordinate `com.acme:api` is used by both `apps/api` and `modules/api-copy`. Give each member a unique [project].group:[project].name.",
                exception.getMessage());
    }

    @Test
    void rejectsDefaultMembersThatAreNotDeclaredMembers() throws IOException {
        workspace(tempDir, """
                [workspace]
                name = "bad"
                members = ["apps/api"]
                defaultMembers = ["apps/worker"]
                """);
        member(tempDir, "apps/api", "api", "com.acme", "");

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertEquals(
                "Workspace default member `apps/worker` must also be listed in [workspace].members.",
                exception.getMessage());
    }

    @Test
    void rejectsDuplicateNormalizedMemberPaths() throws IOException {
        workspace(tempDir, """
                [workspace]
                name = "bad"
                members = ["apps/api", "apps/./api"]
                """);
        member(tempDir, "apps/api", "api", "com.acme", "");

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertEquals(
                "Duplicate workspace member `apps/api` after path normalization.",
                exception.getMessage());
    }
}
