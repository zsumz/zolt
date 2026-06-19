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

final class NativeCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void nativeReportsMissingMainClassClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfigWithoutMain(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "native",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Native Image main class is missing"));
        assertTrue(result.stderr().contains("[project].main"));
    }

    @Test
    void nativeReportsSpringBootNativeRequiresExplicitAotFlag() throws IOException {
        Path projectDir = tempDir.resolve("spring-boot-demo");
        writeSpringBootProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "native",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Spring Boot native images require `[framework.springBoot.native] enabled = true`"));
        assertTrue(result.stderr().contains("Spring Boot JVM build, test, run, and executable packaging"));
        assertTrue(result.stderr().contains("explicit Zolt-owned Spring Boot AOT/native canary path"));
        assertTrue(result.stderr().contains("zolt package --mode spring-boot"));
        assertFalse(result.stderr().contains("not supported by Zolt yet"));
    }

    @Test
    void nativeReportsMissingSpringBootAotToolingClearly() throws IOException {
        Path projectDir = tempDir.resolve("spring-boot-native-demo");
        writeExplicitSpringBootNativeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "native",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Spring Boot native AOT requires tool artifact"));
        assertTrue(result.stderr().contains("Add the Spring Boot platform to [platforms]"));
    }

    @Test
    void nativeBuildsSelectedWorkspaceMemberFromCli() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace-native");
        writeWorkspaceNativeFixture(workspaceDir);
        Path nativeImage = writeFakeNativeImage(tempDir.resolve("native-image"));

        CommandResult result = execute(
                "native",
                "--workspace",
                "--member", "apps/api",
                "--cwd", workspaceDir.toString(),
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
        writeWorkspaceNativeFixture(workspaceDir);

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

    @Test
    void nativeSmokeKeepsReleaseArchiveOutputProjectRelativeFromCli() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfigWithMain(projectDir, "https://repo.maven.apache.org/maven2");
        Path binary = writeFakeNativeBinary(projectDir);

        CommandResult result = execute(
                "native-smoke",
                "--cwd", projectDir.toString(),
                "--binary", Path.of("target/native/zolt").toString(),
                "--work-dir", projectDir.resolve("target/native-smoke").toAbsolutePath().normalize().toString());

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("Native smoke status: ok"));
        assertTrue(Files.exists(projectDir.resolve("target/native-smoke/release/demo-0.1.0-linux-x64.tar.gz")));
        assertTrue(Files.exists(binary));
    }

    @Test
    void nativeSmokeDefaultsWorkDirectoryFromConfiguredOutputRoot() throws IOException {
        Path projectDir = tempDir.resolve("demo-output-root");
        writeProjectConfigWithMain(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(
                projectDir.resolve("zolt.toml"),
                Files.readString(projectDir.resolve("zolt.toml"))
                        .replace("[build]\n", "[build]\n                outputRoot = \".zolt/build\"\n"));
        writeFakeNativeBinary(projectDir);

        CommandResult result = execute(
                "native-smoke",
                "--cwd", projectDir.toString(),
                "--binary", Path.of("target/native/zolt").toString());

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(Files.exists(projectDir.resolve(".zolt/build/native-smoke/release/demo-0.1.0-linux-x64.tar.gz")));
    }

    private static void writeProjectConfigWithoutMain(Path projectDir, String repositoryUrl) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"

                [repositories]
                test = "%s"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """.formatted(currentJavaMajorVersion(), repositoryUrl));
    }

    private static void writeProjectConfigWithMain(Path projectDir, String repositoryUrl) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                main = "com.example.Main"

                [repositories]
                test = "%s"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """.formatted(currentJavaMajorVersion(), repositoryUrl));
    }

    private static void writeSpringBootProjectConfig(Path projectDir, String repositoryUrl) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                main = "com.example.Main"

                [repositories]
                test = "%s"

                [platforms]
                "org.springframework.boot:spring-boot-dependencies" = "4.0.6"

                [dependencies]
                "org.springframework.boot:spring-boot-starter-webmvc" = {}

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """.formatted(currentJavaMajorVersion(), repositoryUrl));
    }

    private static void writeExplicitSpringBootNativeProjectConfig(Path projectDir, String repositoryUrl) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                main = "com.example.Main"

                [repositories]
                test = "%s"

                [framework.springBoot.native]
                enabled = true

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """.formatted(currentJavaMajorVersion(), repositoryUrl));
    }

    private static void writeWorkspaceNativeFixture(Path workspaceDir) throws IOException {
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace-native"
                members = ["apps/api", "modules/core"]
                defaultMembers = ["apps/api"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), """
                [project]
                name = "core"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                """.formatted(currentJavaMajorVersion()));
        Files.createDirectories(coreDir.resolve("src/main/java/com/example/core"));
        Files.writeString(coreDir.resolve("src/main/java/com/example/core/Core.java"), """
                package com.example.core;

                public final class Core {
                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), """
                [project]
                name = "api"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                main = "com.example.api.Api"

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """.formatted(currentJavaMajorVersion()));
        Files.createDirectories(apiDir.resolve("src/main/java/com/example/api"));
        Files.writeString(apiDir.resolve("src/main/java/com/example/api/Api.java"), """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    public static void main(String[] args) {
                        System.out.println(Core.message());
                    }
                }
                """);
    }

    private static Path writeFakeNativeImage(Path binary) throws IOException {
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

    private static Path writeFakeNativeBinary(Path projectDir) throws IOException {
        Path binary = projectDir.resolve("target/native/zolt");
        Files.createDirectories(binary.getParent());
        Files.writeString(binary, """
                #!/usr/bin/env bash
                set -euo pipefail

                command="${1:-}"
                shift || true

                option_value() {
                  local name="$1"
                  shift
                  while [[ "$#" -gt 0 ]]; do
                    if [[ "$1" == "$name" ]]; then
                      shift
                      printf '%s\\n' "$1"
                      return
                    fi
                    shift
                  done
                }

                case "$command" in
                  --version)
                    printf '0.1.0\\n'
                    ;;
                  help)
                    printf 'help\\n'
                    ;;
                  release-archive)
                    cwd="$(option_value --cwd "$@")"
                    output="$(option_value --output "$@")"
                    case "$output" in
                      /*)
                        printf 'Invalid --output path `%s`. Use a project-relative path.\\n' "$output" >&2
                        exit 7
                        ;;
                    esac
                    mkdir -p "$cwd/$output"
                    printf 'archive\\n' > "$cwd/$output/demo-0.1.0-linux-x64.tar.gz"
                    ;;
                  release-verify)
                    printf 'verified\\n'
                    ;;
                  init)
                    cwd="$(option_value --cwd "$@")"
                    mkdir -p "$cwd/hello-native"
                    printf '[project]\\n' > "$cwd/hello-native/zolt.toml"
                    ;;
                  version)
                    action="${1:-}"
                    shift || true
                    cwd="$(option_value --cwd "$@")"
                    if [[ "$action" == "set" ]]; then
                      printf '[project]\\n\\n[versions]\\nnative-smoke = "0.0.1"\\n' > "$cwd/zolt.toml"
                    else
                      printf '[project]\\n' > "$cwd/zolt.toml"
                    fi
                    ;;
                  resolve|build)
                    printf 'ok\\n'
                    ;;
                  package)
                    cwd="$(option_value --cwd "$@")"
                    mkdir -p "$cwd/target"
                    printf 'jar\\n' > "$cwd/target/hello-native-0.1.0.jar"
                    ;;
                  run|run-package)
                    printf 'Hello from hello-native!\\n'
                    ;;
                  *)
                    printf 'unexpected command: %s\\n' "$command" >&2
                    exit 2
                    ;;
                esac
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
