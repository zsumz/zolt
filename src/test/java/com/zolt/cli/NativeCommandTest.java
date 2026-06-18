package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void nativeReportsSpringBootNativeAsUnsupported() throws IOException {
        Path projectDir = tempDir.resolve("spring-boot-demo");
        writeSpringBootProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "native",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Spring Boot native images are not supported"));
        assertTrue(result.stderr().contains("Spring Boot JVM build, test, run, and executable packaging"));
        assertTrue(result.stderr().contains("zolt package --mode spring-boot"));
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
                  run)
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
