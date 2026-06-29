package com.zolt.cli.workspace;

import com.zolt.cli.WorkspaceCommandFixture;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
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

final class WorkspaceBuildCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void buildWorkspaceCompilesMembersInDependencyOrder() throws IOException {
        WorkspaceApplicationFixture fixture = WorkspaceCommandFixture.create(tempDir, "workspace");

        CommandResult result = execute(
                "build",
                "--workspace",
                "--all",
                "--cwd", fixture.apiDir().toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Resolved workspace dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in modules/core"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in apps/api"));
        assertTrue(result.stdout().contains("Compiled 2 workspace main source files"));
        assertTrue(Files.exists(fixture.coreDir().resolve("target/classes/com/example/core/Core.class")));
        assertTrue(Files.exists(fixture.apiDir().resolve("target/classes/com/example/api/Api.class")));
    }

    @Test
    void buildWorkspacePrintsNestedJsonTimingsWhenRequested() throws IOException {
        WorkspaceApplicationFixture fixture = WorkspaceCommandFixture.create(tempDir, "workspace");

        CommandResult result = execute(
                "build",
                "--workspace",
                "--all",
                "--timings",
                "--timings-format", "json",
                "--cwd", fixture.apiDir().toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Compiled 2 workspace main source files"));
        String[] lines = result.stderr().lines().toArray(String[]::new);
        assertEquals(3, lines.length);
        assertTrue(lines[0].contains("\"phase\":\"plan workspace build\""));
        assertTrue(lines[0].contains("\"depth\":1"));
        assertTrue(lines[0].contains("\"includedMembers\":\"2\""));
        assertTrue(lines[0].contains("\"selectedMembers\":\"2\""));
        assertTrue(lines[0].contains("\"resolvedLockfile\":\"true\""));
        assertTrue(lines[1].contains("\"phase\":\"compile workspace members\""));
        assertTrue(lines[1].contains("\"depth\":1"));
        assertTrue(lines[1].contains("\"members\":\"2\""));
        assertTrue(lines[1].contains("\"sourceFiles\":\"2\""));
        assertTrue(lines[1].contains("\"mainCompilationsSkipped\":\"0\""));
        assertTrue(lines[1].contains("\"mainCompilationsExecuted\":\"2\""));
        assertTrue(lines[1].contains("\"mainSourcesRecompiled\""));
        assertTrue(lines[1].contains("\"mainAbiChangedClasses\""));
        assertTrue(lines[1].contains("\"workspaceAbiInvalidations\""));
        assertTrue(lines[2].contains("\"phase\":\"build workspace\""));
        assertTrue(lines[2].contains("\"depth\":0"));
        assertTrue(lines[2].contains("\"members\":\"2\""));
        assertTrue(lines[2].contains("\"sourceFiles\":\"2\""));
        assertTrue(lines[2].contains("\"mainCompilationsSkipped\":\"0\""));
        assertTrue(lines[2].contains("\"mainCompilationsExecuted\":\"2\""));
        assertTrue(lines[2].contains("\"mainSourcesRecompiled\""));
        assertTrue(lines[2].contains("\"workspaceAbiInvalidations\""));
    }

    @Test
    void buildWorkspaceMemberSelectionCompilesDependenciesOnly() throws IOException {
        WorkspaceApplicationFixture fixture = WorkspaceCommandFixture.create(tempDir, "workspace");
        Path workerDir = addMember(fixture.workspaceDir(), "apps/worker", "worker");

        CommandResult result = execute(
                "build",
                "--workspace",
                "--member", "apps/api",
                "--cwd", fixture.apiDir().toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Compiled 1 main source files in modules/core"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in apps/api"));
        assertFalse(result.stdout().contains("apps/worker"));
        assertTrue(Files.exists(fixture.coreDir().resolve("target/classes/com/example/core/Core.class")));
        assertTrue(Files.exists(fixture.apiDir().resolve("target/classes/com/example/api/Api.class")));
        assertFalse(Files.exists(workerDir.resolve("target/classes/com/example/worker/Worker.class")));
    }

    @Test
    void buildWorkspaceMembersOptionSelectsCommaSeparatedMembers() throws IOException {
        WorkspaceApplicationFixture fixture = WorkspaceCommandFixture.create(tempDir, "workspace");
        Path workerDir = addMember(fixture.workspaceDir(), "apps/worker", "worker");
        Path adminDir = addMember(fixture.workspaceDir(), "apps/admin", "admin");
        Files.writeString(fixture.workspaceDir().resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core", "apps/worker", "apps/admin"]
                """);

        CommandResult result = execute(
                "build",
                "--workspace",
                "--members", "apps/api,apps/worker",
                "--cwd", fixture.apiDir().toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Compiled 1 main source files in modules/core"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in apps/api"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in apps/worker"));
        assertFalse(result.stdout().contains("apps/admin"));
        assertTrue(Files.exists(fixture.coreDir().resolve("target/classes/com/example/core/Core.class")));
        assertTrue(Files.exists(fixture.apiDir().resolve("target/classes/com/example/api/Api.class")));
        assertTrue(Files.exists(workerDir.resolve("target/classes/com/example/worker/Worker.class")));
        assertFalse(Files.exists(adminDir.resolve("target/classes/com/example/admin/Admin.class")));
    }

    @Test
    void workspaceMembersOptionConflictsWithAll() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Files.createDirectories(apiDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));

        CommandResult result = execute(
                "build",
                "--workspace",
                "--all",
                "--members", "apps/api",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Use either --all or member selection for workspace selection, not both."));
    }

    private static Path addMember(Path workspaceDir, String memberPath, String name) throws IOException {
        Path memberDir = workspaceDir.resolve(memberPath);
        Files.createDirectories(memberDir);
        Files.writeString(memberDir.resolve("zolt.toml"), memberConfig(name));
        Path source = memberDir.resolve("src/main/java/com/example/" + name + "/" + capitalized(name) + ".java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.example.%s;

                public final class %s {
                }
                """.formatted(name, capitalized(name)));
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core", "%s"]
                """.formatted(memberPath));
        return memberDir;
    }

    private static String capitalized(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
