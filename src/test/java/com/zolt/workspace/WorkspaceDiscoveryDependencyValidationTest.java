package com.zolt.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceDiscoveryDependencyValidationTest {
    private final WorkspaceDiscoveryService service = new WorkspaceDiscoveryService();

    @TempDir
    private Path tempDir;

    @Test
    void rejectsWorkspaceDependencyCycle() throws IOException {
        workspace("""
                [workspace]
                name = "bad"
                members = ["apps/api", "modules/core", "modules/util"]
                """);
        member("apps/api", "api", "com.acme", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        member("modules/core", "core", "com.acme", """

                [dependencies]
                "com.acme:util" = { workspace = "modules/util" }
                """);
        member("modules/util", "util", "com.acme", """

                [dependencies]
                "com.acme:api" = { workspace = "apps/api" }
                """);

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertEquals(
                "Workspace dependency cycle detected: apps/api -> modules/core -> modules/util -> apps/api.",
                exception.getMessage());
    }

    @Test
    void rejectsWorkspaceDependencyTargetThatIsNotAMember() throws IOException {
        workspace("""
                [workspace]
                name = "bad"
                members = ["apps/api"]
                """);
        member("apps/api", "api", "com.acme", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertEquals(
                "Workspace dependency `com.acme:core` in member `apps/api` points to `modules/core`, but that path is not listed in [workspace].members.",
                exception.getMessage());
    }

    @Test
    void rejectsWorkspaceDependencyPathThatEscapesWorkspaceRoot() throws IOException {
        workspace("""
                [workspace]
                name = "bad"
                members = ["apps/api"]
                """);
        member("apps/api", "api", "com.acme", """

                [dependencies]
                "com.acme:core" = { workspace = "../outside" }
                """);

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertTrue(exception.getMessage().contains("[dependencies].com.acme:core.workspace"));
        assertTrue(exception.getMessage().contains("../outside"));
    }

    @Test
    void rejectsWorkspaceDependencyCoordinateMismatch() throws IOException {
        workspace("""
                [workspace]
                name = "bad"
                members = ["apps/api", "modules/core"]
                """);
        member("apps/api", "api", "com.acme", """

                [dependencies]
                "com.acme:not-core" = { workspace = "modules/core" }
                """);
        member("modules/core", "core", "com.acme");

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertEquals(
                "Workspace dependency `com.acme:not-core` in member `apps/api` points to `modules/core`, whose project coordinate is `com.acme:core`. Update the dependency key or workspace path so they match.",
                exception.getMessage());
    }

    @Test
    void rejectsWorkspaceSelfDependency() throws IOException {
        workspace("""
                [workspace]
                name = "bad"
                members = ["apps/api"]
                """);
        member("apps/api", "api", "com.acme", """

                [dependencies]
                "com.acme:api" = { workspace = "apps/api" }
                """);

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertEquals(
                "Workspace member `apps/api` cannot depend on itself through `com.acme:api`.",
                exception.getMessage());
    }

    private void workspace(String content) throws IOException {
        Files.writeString(tempDir.resolve("zolt-workspace.toml"), content);
    }

    private void member(String path, String name, String group) throws IOException {
        member(path, name, group, "");
    }

    private void member(String path, String name, String group, String extraToml) throws IOException {
        Path member = tempDir.resolve(path);
        Files.createDirectories(member);
        Files.writeString(member.resolve("zolt.toml"), """
                [project]
                name = "%s"
                version = "0.1.0"
                group = "%s"
                java = "21"
                %s""".formatted(name, group, extraToml));
    }
}
