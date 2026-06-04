package com.zolt.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.JavacException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceBuildServiceTest {
    private final WorkspaceBuildService service = new WorkspaceBuildService();

    @TempDir
    private Path tempDir;

    @Test
    void buildsWorkspaceMembersInDependencyOrder() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]
                """);
        member("modules/core", "core", "");
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);

        WorkspaceBuildResult result = service.build(tempDir.resolve("apps/api"), tempDir.resolve("cache"), false);

        assertTrue(result.resolvedLockfile());
        assertEquals(List.of("modules/core", "apps/api"), result.members().stream()
                .map(WorkspaceBuildResult.MemberBuildResult::member)
                .toList());
        assertEquals(2, result.sourceCount());
        assertTrue(Files.exists(tempDir.resolve("modules/core/target/classes/com/acme/core/Core.class")));
        assertTrue(Files.exists(tempDir.resolve("apps/api/target/classes/com/acme/api/Api.class")));
        assertTrue(Files.exists(tempDir.resolve("zolt.lock")));
    }

    @Test
    void buildsSelectedWorkspaceMembersAndDependencies() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core", "apps/worker"]
                """);
        member("modules/core", "core", "");
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);
        member("apps/worker", "worker", "");
        source("apps/worker/src/main/java/com/acme/worker/Worker.java", """
                package com.acme.worker;

                public final class Worker {
                }
                """);

        WorkspaceBuildResult result = service.build(
                tempDir,
                tempDir.resolve("cache"),
                false,
                new WorkspaceSelectionRequest(false, List.of("apps/api")));

        assertEquals(List.of("modules/core", "apps/api"), result.members().stream()
                .map(WorkspaceBuildResult.MemberBuildResult::member)
                .toList());
        assertEquals(2, result.sourceCount());
        assertTrue(Files.exists(tempDir.resolve("modules/core/target/classes/com/acme/core/Core.class")));
        assertTrue(Files.exists(tempDir.resolve("apps/api/target/classes/com/acme/api/Api.class")));
        assertFalse(Files.exists(tempDir.resolve("apps/worker/target/classes/com/acme/worker/Worker.class")));
    }

    @Test
    void failsWhenMemberImportsWorkspaceProjectWithoutDeclaringDependency() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["modules/core", "modules/extra", "apps/api", "apps/worker"]
                """);
        member("modules/core", "core", "");
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        member("modules/extra", "extra", "");
        source("modules/extra/src/main/java/com/acme/extra/Extra.java", """
                package com.acme.extra;

                public final class Extra {
                    private Extra() {
                    }

                    public static String message() {
                        return "extra";
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;
                import com.acme.extra.Extra;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message() + Extra.message();
                    }
                }
                """);
        member("apps/worker", "worker", """

                [dependencies]
                "com.acme:extra" = { workspace = "modules/extra" }
                """);
        source("apps/worker/src/main/java/com/acme/worker/Worker.java", """
                package com.acme.worker;

                import com.acme.extra.Extra;

                public final class Worker {
                    private Worker() {
                    }

                    public static String message() {
                        return Extra.message();
                    }
                }
                """);

        JavacException exception = assertThrows(
                JavacException.class,
                () -> service.build(
                        tempDir,
                        tempDir.resolve("cache"),
                        false,
                        new WorkspaceSelectionRequest(true, List.of())));

        assertTrue(exception.getMessage().contains("javac failed with exit code"));
        assertTrue(exception.getMessage().contains("Api.java"));
        assertTrue(exception.getMessage().contains("com.acme.extra"));
    }

    private void workspace(String content) throws IOException {
        Files.writeString(tempDir.resolve("zolt-workspace.toml"), content);
    }

    private void member(String path, String name, String extraToml) throws IOException {
        Path member = tempDir.resolve(path);
        Files.createDirectories(member);
        Files.writeString(member.resolve("zolt.toml"), """
                [project]
                name = "%s"
                version = "0.1.0"
                group = "com.acme"
                java = "%s"
                %s""".formatted(name, currentJavaMajorVersion(), extraToml));
    }

    private void source(String path, String content) throws IOException {
        Path source = tempDir.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
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
