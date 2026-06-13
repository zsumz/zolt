package com.zolt.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceDiscoveryServiceTest {
    private final WorkspaceDiscoveryService service = new WorkspaceDiscoveryService();

    @TempDir
    private Path tempDir;

    @Test
    void discoversWorkspaceFromNestedMemberDirectory() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]
                defaultMembers = ["apps/api"]

                [repositories]
                internal = "https://repo.acme.example/maven"

                [platforms]
                "com.acme:enterprise-platform" = "2026.1.0"
                """);
        member("apps/api", "api", "com.acme");
        member("modules/core", "core", "com.acme");
        Path nestedDirectory = tempDir.resolve("apps/api/src/main/java/com/acme");
        Files.createDirectories(nestedDirectory);

        Workspace workspace = service.discover(nestedDirectory).orElseThrow();

        assertEquals(tempDir.toAbsolutePath().normalize(), workspace.root());
        assertEquals(tempDir.resolve("zolt-workspace.toml").toAbsolutePath().normalize(), workspace.configPath());
        assertEquals("acme-platform", workspace.config().name());
        assertEquals(List.of("apps/api", "modules/core"), workspace.config().members());
        assertEquals(List.of("apps/api"), workspace.config().defaultMembers());
        assertEquals("https://repo.acme.example/maven", workspace.config().repositories().get("internal"));
        assertEquals("2026.1.0", workspace.config().platforms().get("com.acme:enterprise-platform"));
        assertEquals(List.of("apps/api", "modules/core"), workspace.members().stream()
                .map(WorkspaceMember::path)
                .toList());
        assertEquals(List.of("api", "core"), workspace.members().stream()
                .map(member -> member.config().project().name())
                .toList());
        assertEquals(List.of("apps/api", "modules/core"), workspace.buildOrder());
    }

    @Test
    void returnsEmptyWhenNoWorkspaceIsFound() throws IOException {
        Path project = tempDir.resolve("project");
        Files.createDirectories(project);

        assertFalse(service.discover(project).isPresent());
    }

    @Test
    void rejectsMemberPathsThatEscapeWorkspaceRoot() throws IOException {
        workspace("""
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
        workspace("""
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
        workspace("""
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
        workspace("""
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
    void rejectsDuplicateMemberCoordinates() throws IOException {
        workspace("""
                [workspace]
                name = "bad"
                members = ["apps/api", "modules/api-copy"]
                """);
        member("apps/api", "api", "com.acme");
        member("modules/api-copy", "api", "com.acme");

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertEquals(
                "Workspace member coordinate `com.acme:api` is used by both `apps/api` and `modules/api-copy`. Give each member a unique [project].group:[project].name.",
                exception.getMessage());
    }

    @Test
    void rejectsDefaultMembersThatAreNotDeclaredMembers() throws IOException {
        workspace("""
                [workspace]
                name = "bad"
                members = ["apps/api"]
                defaultMembers = ["apps/worker"]
                """);
        member("apps/api", "api", "com.acme");

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertEquals(
                "Workspace default member `apps/worker` must also be listed in [workspace].members.",
                exception.getMessage());
    }

    @Test
    void rejectsDuplicateNormalizedMemberPaths() throws IOException {
        workspace("""
                [workspace]
                name = "bad"
                members = ["apps/api", "apps/./api"]
                """);
        member("apps/api", "api", "com.acme");

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> service.load(tempDir));

        assertEquals(
                "Duplicate workspace member `apps/api` after path normalization.",
                exception.getMessage());
    }

    @Test
    void createsEdgesForWorkspaceDependencies() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core", "modules/test-fixtures"]
                """);
        member("apps/api", "api", "com.acme", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }

                [test.dependencies]
                "com.acme:test-fixtures" = { workspace = "modules/test-fixtures" }
                """);
        member("modules/core", "core", "com.acme");
        member("modules/test-fixtures", "test-fixtures", "com.acme");

        Workspace workspace = service.load(tempDir);

        assertEquals(
                List.of(
                        new WorkspaceProjectEdge("apps/api", "modules/core", "compile", "com.acme:core"),
                        new WorkspaceProjectEdge(
                                "apps/api",
                                "modules/test-fixtures",
                                "test",
                                "com.acme:test-fixtures")),
                workspace.edges());
    }

    @Test
    void createsExportedEdgesForWorkspaceApiDependencies() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]
                """);
        member("apps/api", "api", "com.acme", """

                [api.dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        member("modules/core", "core", "com.acme");

        Workspace workspace = service.load(tempDir);

        assertEquals(
                List.of(new WorkspaceProjectEdge("apps/api", "modules/core", "compile", "com.acme:core", true)),
                workspace.edges());
    }

    @Test
    void createsDependencyFirstBuildOrder() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core", "apps/worker"]
                """);
        member("apps/api", "api", "com.acme", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        member("modules/core", "core", "com.acme");
        member("apps/worker", "worker", "com.acme");

        Workspace workspace = service.load(tempDir);

        assertEquals(List.of("modules/core", "apps/api", "apps/worker"), workspace.buildOrder());
    }

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

    private static void createSymlink(Path link, Path target) throws IOException {
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException exception) {
            Assumptions.assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
        }
    }
}
