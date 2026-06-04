package com.zolt.workspace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceClasspathServiceTest {
    private final WorkspaceClasspathService service = new WorkspaceClasspathService();
    private final ZoltLockfileReader lockfileReader = new ZoltLockfileReader();

    @TempDir
    private Path tempDir;

    @Test
    void filtersWorkspaceOutputsToMemberDependencyClosure() throws IOException {
        Workspace workspace = workspace(
                List.of("apps/api", "apps/worker", "modules/core", "modules/extra"),
                List.of(
                        new WorkspaceProjectEdge("apps/api", "modules/core", "compile", "com.acme:core"),
                        new WorkspaceProjectEdge("apps/worker", "modules/extra", "compile", "com.acme:extra")));
        Path coreOutput = tempDir.resolve("modules/core/target/classes").normalize();
        Path extraOutput = tempDir.resolve("modules/extra/target/classes").normalize();
        Path coreHelperJar = tempDir.resolve("cache/org/example/core-helper/1.0.0/core-helper-1.0.0.jar");
        Path workerHelperJar = tempDir.resolve("cache/org/example/worker-helper/1.0.0/worker-helper-1.0.0.jar");
        Path legacyJar = tempDir.resolve("cache/org/example/legacy/1.0.0/legacy-1.0.0.jar");
        createEmptyFile(coreHelperJar);
        createEmptyFile(workerHelperJar);
        createEmptyFile(legacyJar);
        ZoltLockfile lockfile = lockfileReader.read("""
                version = 1

                [[package]]
                id = "com.acme:core"
                version = "0.1.0"
                source = "workspace"
                scope = "compile"
                direct = true
                workspace = "modules/core"
                workspaceOutput = "target/classes"
                members = ["apps/api"]
                dependencies = []

                [[package]]
                id = "com.acme:extra"
                version = "0.1.0"
                source = "workspace"
                scope = "compile"
                direct = true
                workspace = "modules/extra"
                workspaceOutput = "target/classes"
                members = ["apps/worker"]
                dependencies = []

                [[package]]
                id = "org.example:core-helper"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "org/example/core-helper/1.0.0/core-helper-1.0.0.jar"
                members = ["modules/core"]
                dependencies = []

                [[package]]
                id = "org.example:worker-helper"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "org/example/worker-helper/1.0.0/worker-helper-1.0.0.jar"
                members = ["apps/worker"]
                dependencies = []

                [[package]]
                id = "org.example:legacy"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "org/example/legacy/1.0.0/legacy-1.0.0.jar"
                dependencies = []
                """);

        ClasspathSet apiClasspaths = service.classpathsFor(workspace, lockfile, tempDir.resolve("cache"), "apps/api");
        ClasspathSet workerClasspaths = service.classpathsFor(workspace, lockfile, tempDir.resolve("cache"), "apps/worker");

        assertTrue(apiClasspaths.compile().entries().contains(coreOutput));
        assertFalse(apiClasspaths.compile().entries().contains(extraOutput));
        assertTrue(apiClasspaths.compile().entries().contains(coreHelperJar));
        assertFalse(apiClasspaths.compile().entries().contains(workerHelperJar));
        assertTrue(apiClasspaths.compile().entries().contains(legacyJar));
        assertTrue(workerClasspaths.compile().entries().contains(extraOutput));
        assertFalse(workerClasspaths.compile().entries().contains(coreOutput));
        assertFalse(workerClasspaths.compile().entries().contains(coreHelperJar));
        assertTrue(workerClasspaths.compile().entries().contains(workerHelperJar));
        assertTrue(workerClasspaths.compile().entries().contains(legacyJar));
    }

    private Workspace workspace(
            List<String> members,
            List<WorkspaceProjectEdge> edges) throws IOException {
        Files.writeString(tempDir.resolve("zolt-workspace.toml"), "");
        return new Workspace(
                tempDir,
                tempDir.resolve("zolt-workspace.toml"),
                new WorkspaceConfig("acme-platform", members, List.of(), Map.of(), Map.of()),
                members.stream()
                        .map(member -> new WorkspaceMember(member, tempDir.resolve(member), null))
                        .toList(),
                edges,
                members);
    }

    private static void createEmptyFile(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "");
    }
}
