package com.zolt.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.build.NativeImageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceNativeBuildServiceTest {
    private final WorkspaceNativeBuildService service = new WorkspaceNativeBuildService();

    @TempDir
    private Path tempDir;

    @Test
    void buildsNativeImageForDefaultWorkspaceApplicationMember() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]
                defaultMembers = ["apps/api"]
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
                main = "com.acme.api.Api"

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static void main(String[] args) {
                        System.out.println(Core.message());
                    }
                }
                """);
        Path nativeImage = fakeNativeImage(tempDir.resolve("native-image"));

        WorkspaceNativeBuildResult result = service.buildNative(
                tempDir,
                tempDir.resolve("cache"),
                WorkspaceSelectionRequest.defaults(),
                nativeImage);

        assertTrue(result.resolvedLockfile());
        assertEquals(List.of("modules/core", "apps/api"), result.builtMembers().stream()
                .map(WorkspaceBuildResult.MemberBuildResult::member)
                .toList());
        assertEquals(List.of("apps/api"), result.members().stream()
                .map(WorkspaceNativeBuildResult.MemberNativeBuildResult::member)
                .toList());
        Path binary = tempDir.resolve("apps/api/target/native/api");
        assertTrue(Files.exists(binary));
        assertFalse(Files.exists(tempDir.resolve("modules/core/target/native/core")));
        String log = Files.readString(tempDir.resolve("apps/api/target/native/native-image.log"));
        assertTrue(log.contains(tempDir.resolve("apps/api/target/api-0.1.0.jar").toString()));
        assertTrue(log.contains(tempDir.resolve("modules/core/target/classes").toString()));
    }

    @Test
    void selectedLibraryMemberWithoutMainClassProducesWorkspaceDiagnostic() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["modules/core"]
                """);
        member("modules/core", "core", "");
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                public final class Core {
                }
                """);

        NativeImageException exception = assertThrows(
                NativeImageException.class,
                () -> service.buildNative(
                        tempDir,
                        tempDir.resolve("cache"),
                        new WorkspaceSelectionRequest(false, List.of("modules/core")),
                        fakeNativeImage(tempDir.resolve("native-image"))));

        assertEquals(
                "Workspace member `modules/core` has no main class configured. Add [project].main to its zolt.toml or choose an application member.",
                exception.getMessage());
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

    private static Path fakeNativeImage(Path binary) throws IOException {
        Files.writeString(binary, """
                #!/usr/bin/env bash
                set -euo pipefail

                classpath=""
                output=""
                while [[ "$#" -gt 0 ]]; do
                  case "$1" in
                    -cp)
                      shift
                      classpath="$1"
                      ;;
                    -o)
                      shift
                      output="$1"
                      ;;
                  esac
                  shift || true
                done

                mkdir -p "$(dirname "$output")"
                printf 'native\\n' > "$output"
                printf 'classpath=%s\\n' "$classpath"
                printf 'output=%s\\n' "$output"
                """);
        assertTrue(binary.toFile().setExecutable(true));
        return binary;
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
