package com.zolt.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.CleanException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceCleanServiceTest {
    @TempDir
    private Path tempDir;

    private final WorkspaceCleanService service = new WorkspaceCleanService();

    @Test
    void cleansSelectedWorkspaceMembersAndDependenciesInBuildOrder() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core", "apps/worker"]
                """);
        member("modules/core", "core", "");
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        member("apps/worker", "worker", "");
        output("modules/core/target/classes/Core.class");
        output("apps/api/target/classes/Api.class");
        output("apps/worker/target/classes/Worker.class");

        WorkspaceCleanResult result = service.clean(
                tempDir,
                new WorkspaceSelectionRequest(false, List.of("apps/api")));

        assertEquals(List.of("modules/core", "apps/api"), result.selection().includedMembers());
        assertEquals(List.of("modules/core", "apps/api"), result.members().stream()
                .map(WorkspaceCleanResult.MemberCleanResult::member)
                .toList());
        assertEquals(2, result.deletedCount());
        assertFalse(Files.exists(tempDir.resolve("modules/core/target")));
        assertFalse(Files.exists(tempDir.resolve("apps/api/target")));
        assertTrue(Files.exists(tempDir.resolve("apps/worker/target/classes/Worker.class")));
    }

    @Test
    void cleansAllWorkspaceMembersWhenAllIsRequested() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["modules/core", "apps/api"]
                """);
        member("modules/core", "core", "");
        member("apps/api", "api", "");
        output("modules/core/target/classes/Core.class");
        output("apps/api/target/classes/Api.class");

        WorkspaceCleanResult result = service.clean(
                tempDir,
                new WorkspaceSelectionRequest(true, List.of()));

        assertEquals(List.of("modules/core", "apps/api"), result.selection().includedMembers());
        assertEquals(2, result.deletedCount());
        assertFalse(Files.exists(tempDir.resolve("modules/core/target")));
        assertFalse(Files.exists(tempDir.resolve("apps/api/target")));
    }

    @Test
    void doesNotRequireWorkspaceLockfile() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api"]
                """);
        member("apps/api", "api", "");
        output("apps/api/target/classes/Api.class");

        WorkspaceCleanResult result = service.clean(tempDir, WorkspaceSelectionRequest.defaults());

        assertEquals(1, result.deletedCount());
        assertFalse(Files.exists(tempDir.resolve("zolt.lock")));
        assertFalse(Files.exists(tempDir.resolve("apps/api/target")));
    }

    @Test
    void preservesMemberAndGlobalCaches() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api"]
                """);
        member("apps/api", "api", "");
        output("apps/api/target/classes/Api.class");
        output("apps/api/.zolt/cache/artifact.jar");
        output(".zolt/cache/artifact.jar");

        service.clean(tempDir, WorkspaceSelectionRequest.defaults());

        assertFalse(Files.exists(tempDir.resolve("apps/api/target")));
        assertTrue(Files.exists(tempDir.resolve("apps/api/.zolt/cache/artifact.jar")));
        assertTrue(Files.exists(tempDir.resolve(".zolt/cache/artifact.jar")));
    }

    @Test
    void addsMemberContextToCleanFailures() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api"]
                """);
        member("apps/api", "api", """

                [build]
                output = "../outside/classes"
                testOutput = "target/test-classes"
                """);

        CleanException exception = assertThrows(
                CleanException.class,
                () -> service.clean(tempDir, WorkspaceSelectionRequest.defaults()));

        assertTrue(exception.getMessage().contains("Workspace member `apps/api` could not be cleaned."));
        assertTrue(exception.getMessage().contains("[build].output"));
        assertTrue(exception.getMessage().contains("../outside/classes"));
    }

    private void workspace(String content) throws IOException {
        Files.writeString(tempDir.resolve("zolt-workspace.toml"), content);
    }

    private void member(String path, String name, String extraToml) throws IOException {
        Path directory = tempDir.resolve(path);
        Files.createDirectories(directory);
        String buildSection = extraToml.contains("[build]")
                ? extraToml
                : """
                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                %s
                """.formatted(extraToml);
        Files.writeString(directory.resolve("zolt.toml"), """
                [project]
                name = "%s"
                version = "0.1.0"
                group = "com.acme"
                java = "%s"

                [repositories]
                central = "https://repo.maven.apache.org/maven2"

                %s
                """.formatted(name, currentJavaMajorVersion(), buildSection));
    }

    private void output(String path) throws IOException {
        Path output = tempDir.resolve(path);
        Files.createDirectories(output.getParent());
        Files.writeString(output, "output");
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }
}
