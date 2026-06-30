package com.zolt.workspace.discovery;

import static com.zolt.workspace.discovery.WorkspaceDiscoveryServiceTestSupport.member;
import static com.zolt.workspace.discovery.WorkspaceDiscoveryServiceTestSupport.rootWorkspace;
import static com.zolt.workspace.discovery.WorkspaceDiscoveryServiceTestSupport.workspace;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.workspace.Workspace;
import com.zolt.workspace.WorkspaceMember;
import com.zolt.workspace.WorkspaceProjectEdge;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceDiscoveryServiceTest {
    private final WorkspaceDiscoveryService service = new WorkspaceDiscoveryService();

    @TempDir
    private Path tempDir;

    @Test
    void discoversWorkspaceFromNestedMemberDirectory() throws IOException {
        workspace(tempDir, """
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]
                defaultMembers = ["apps/api"]

                [repositories]
                internal = "https://repo.acme.example/maven"

                [platforms]
                "com.acme:enterprise-platform" = "2026.1.0"
                """);
        member(tempDir, "apps/api", "api", "com.acme", "");
        member(tempDir, "modules/core", "core", "com.acme", "");
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
    void discoversRootWorkspaceConfigFromNestedMemberDirectory() throws IOException {
        rootWorkspace(tempDir, """
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]
                defaultMembers = ["apps/api"]

                [repositories]
                internal = "https://repo.acme.example/maven"

                [platforms]
                "com.acme:enterprise-platform" = "2026.1.0"
                """);
        member(tempDir, "apps/api", "api", "com.acme", "");
        member(tempDir, "modules/core", "core", "com.acme", "");
        Path nestedDirectory = tempDir.resolve("apps/api/src/main/java/com/acme");
        Files.createDirectories(nestedDirectory);

        Workspace workspace = service.discover(nestedDirectory).orElseThrow();

        assertEquals(tempDir.toAbsolutePath().normalize(), workspace.root());
        assertEquals(tempDir.resolve("zolt.toml").toAbsolutePath().normalize(), workspace.configPath());
        assertEquals("acme-platform", workspace.config().name());
        assertEquals(List.of("apps/api", "modules/core"), workspace.config().members());
        assertEquals(List.of("apps/api"), workspace.config().defaultMembers());
        assertEquals("https://repo.acme.example/maven", workspace.config().repositories().get("internal"));
        assertEquals("2026.1.0", workspace.config().platforms().get("com.acme:enterprise-platform"));
    }

    @Test
    void loadsRootWorkspaceConfigWithRootAsMember() throws IOException {
        rootWorkspace(tempDir, """
                [project]
                name = "api"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [workspace]
                name = "acme-platform"
                members = [".", "modules/core"]
                defaultMembers = ["."]

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        member(tempDir, "modules/core", "core", "com.acme", "");

        Workspace workspace = service.load(tempDir);

        assertEquals(List.of(".", "modules/core"), workspace.members().stream()
                .map(WorkspaceMember::path)
                .toList());
        assertEquals(List.of("modules/core", "."), workspace.buildOrder());
    }

    @Test
    void returnsEmptyWhenNoWorkspaceIsFound() throws IOException {
        Path project = tempDir.resolve("project");
        Files.createDirectories(project);

        assertFalse(service.discover(project).isPresent());
    }

    @Test
    void ignoresRootZoltTomlWithoutWorkspaceSection() throws IOException {
        Files.writeString(tempDir.resolve("zolt.toml"), """
                [project]
                name = "api"
                version = "0.1.0"
                group = "com.acme"
                java = "21"
                """);

        assertFalse(service.discover(tempDir).isPresent());
    }

    @Test
    void createsEdgesForWorkspaceDependencies() throws IOException {
        workspace(tempDir, """
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core", "modules/test-fixtures"]
                """);
        member(tempDir, "apps/api", "api", "com.acme", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }

                [test.dependencies]
                "com.acme:test-fixtures" = { workspace = "modules/test-fixtures" }
                """);
        member(tempDir, "modules/core", "core", "com.acme", "");
        member(tempDir, "modules/test-fixtures", "test-fixtures", "com.acme", "");

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
        workspace(tempDir, """
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]
                """);
        member(tempDir, "apps/api", "api", "com.acme", """

                [api.dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        member(tempDir, "modules/core", "core", "com.acme", "");

        Workspace workspace = service.load(tempDir);

        assertEquals(
                List.of(new WorkspaceProjectEdge("apps/api", "modules/core", "compile", "com.acme:core", true)),
                workspace.edges());
    }

    @Test
    void createsDependencyFirstBuildOrder() throws IOException {
        workspace(tempDir, """
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core", "apps/worker"]
                """);
        member(tempDir, "apps/api", "api", "com.acme", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        member(tempDir, "modules/core", "core", "com.acme", "");
        member(tempDir, "apps/worker", "worker", "com.acme", "");

        Workspace workspace = service.load(tempDir);

        assertEquals(List.of("modules/core", "apps/api", "apps/worker"), workspace.buildOrder());
    }
}
