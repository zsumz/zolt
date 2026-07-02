package sh.zolt.cli.publish;

import static sh.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReleaseCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void releaseArchiveAssemblesArchiveFromNativeBinary() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("README.md"), "# Demo\n");
        Path binary = projectDir.resolve("target/native/demo");
        Files.createDirectories(binary.getParent());
        Files.writeString(binary, "native");
        CommandResult result = execute(
                "--progress=always",
                "release-archive",
                "--directory", projectDir.toString(),
                "--target", "linux-x64");
        Path archive = projectDir.resolve("dist/demo-0.1.0-linux-x64.tar.gz");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("✔ Assembled linux-x64 release archive"));
        assertTrue(result.stdout().contains("3 files"));
        assertTrue(result.stdout().contains("root demo-0.1.0-linux-x64"));
        assertTrue(result.stdout().contains("→ wrote " + archive));
        assertTrue(result.stdout().contains("→ wrote " + archive + ".sha256"));
        assertTrue(result.stdout().contains("→ wrote " + projectDir.resolve("dist/release-manifest.json")));
        assertTrue(result.stderr().contains("Assembling release archive..."));
        assertTrue(result.stderr().contains("Assembled linux-x64 release archive"));
        assertTrue(Files.exists(archive));
        assertTrue(Files.exists(projectDir.resolve("dist/demo-0.1.0-linux-x64.tar.gz.sha256")));
        assertTrue(Files.exists(projectDir.resolve("dist/release-manifest.json")));
    }

    @Test
    void releaseArchiveUsesModernHumanOutputControls() throws IOException {
        Path projectDir = tempDir.resolve("archive-output");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Path binary = projectDir.resolve("target/native/demo");
        Files.createDirectories(binary.getParent());
        Files.writeString(binary, "native");
        CommandResult color = execute(
                "--color=always",
                "--progress=always",
                "release-archive",
                "--directory", projectDir.toString(),
                "--target", "linux-x64",
                "--output", "dist-color");
        CommandResult quiet = execute(
                "--quiet",
                "release-archive",
                "--directory", projectDir.toString(),
                "--target", "linux-x64",
                "--output", "dist-quiet");
        Path archive = projectDir.resolve("dist-color/demo-0.1.0-linux-x64.tar.gz");
        assertEquals(0, color.exitCode(), color.stderr());
        assertTrue(color.stdout().contains("\u001B[32m✔\u001B[0m Assembled linux-x64 release archive"));
        assertFalse(color.stdout().contains("\u001B[32mAssembled linux-x64 release archive\u001B[0m"));
        assertTrue(color.stdout().contains("2 files\u001B[0m"));
        assertTrue(color.stdout().contains("root demo-0.1.0-linux-x64\u001B[0m"));
        assertTrue(color.stdout().contains("\u001B[36m→\u001B[0m wrote \u001B[36m" + archive + "\u001B[0m"));
        assertFalse(color.stdout().contains("\u001B[32mWrote archive to "));
        assertTrue(color.stdout().contains("\u001B[36m→\u001B[0m wrote \u001B[36m" + archive + ".sha256\u001B[0m"));
        assertFalse(color.stdout().contains("\u001B[32mWrote checksum to "));
        assertTrue(color.stdout().contains("\u001B[36m→\u001B[0m wrote \u001B[36m"
                + projectDir.resolve("dist-color/release-manifest.json") + "\u001B[0m"));
        assertFalse(color.stdout().contains("\u001B[32mWrote manifest to "));
        assertTrue(color.stderr().contains("\u001B[36mAssembling\u001B[0m release archive..."));
        assertTrue(color.stderr().contains("\u001B[32mAssembled\u001B[0m linux-x64 release archive"));
        assertFalse(color.stderr().contains("\u001B[36mAssembling release archive...")
                || color.stderr().contains("\u001B[32mAssembled linux-x64 release archive"));
        assertEquals(0, quiet.exitCode(), quiet.stderr());
        assertEquals("", quiet.stdout());
        assertTrue(Files.exists(projectDir.resolve("dist-quiet/demo-0.1.0-linux-x64.tar.gz")));
    }

    @Test
    void releaseArchiveReportsMissingBinaryClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "release-archive",
                "--cwd", projectDir.toString(),
                "--target", "linux-x64");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Release archive requires native binary"));
        assertTrue(result.stderr().contains("Run `zolt native` or pass --binary <path>"));
    }

    @Test
    void releaseVerifyReportsMissingArchiveClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        CommandResult result = execute(
                "release-verify",
                "--cwd", projectDir.toString(),
                "dist/missing.tar.gz");
        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Release archive verification failed for"));
        assertTrue(result.stderr().contains("archive does not exist"));
        assertTrue(result.stderr().contains("Pass a valid release archive path"));
    }

    @Test
    void releaseVerifyUsesModernHumanOutputControls() throws IOException {
        Path projectDir = tempDir.resolve("verify-output");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeFakeZoltBinary(projectDir.resolve("target/native/zolt"));
        CommandResult archiveResult = execute(
                "release-archive",
                "--directory", projectDir.toString(),
                "--target", "linux-x64",
                "--binary", "target/native/zolt");
        Path archive = projectDir.resolve("dist/demo-0.1.0-linux-x64.tar.gz");
        assertEquals(0, archiveResult.exitCode(), archiveResult.stderr());
        CommandResult color = execute(
                "--color=always",
                "--progress=always",
                "release-verify",
                "--directory", projectDir.toString(),
                "--work-dir", "target/release-verify-color",
                "dist/demo-0.1.0-linux-x64.tar.gz");
        CommandResult quiet = execute(
                "--quiet",
                "release-verify",
                "--directory", projectDir.toString(),
                "--work-dir", "target/release-verify-quiet",
                "dist/demo-0.1.0-linux-x64.tar.gz");
        assertEquals(0, color.exitCode(), color.stderr());
        assertTrue(color.stdout().contains("Release verify status: \u001B[32mok\u001B[0m"));
        assertFalse(color.stdout().contains("\u001B[32mRelease\u001B[0m verify status"));
        assertTrue(color.stdout().contains("\u001B[32mok:\u001B[0m Verified release archive " + archive));
        assertFalse(color.stdout().contains("\u001B[32mok: Verified release archive"));
        assertTrue(color.stdout().contains("Unpacked to: "));
        assertFalse(color.stdout().contains("\u001B[32mUnpacked to "));
        assertTrue(color.stdout().contains("\u001B[32mok:\u001B[0m Ran smoke binary"));
        assertFalse(color.stdout().contains("\u001B[32mok: Ran smoke binary"));
        assertTrue(color.stdout().contains("Archives verified: 1"));
        assertFalse(color.stdout().contains("\u001B[32mArchives verified"));
        assertTrue(color.stderr().contains("\u001B[36mVerifying\u001B[0m release archives..."));
        assertTrue(color.stderr().contains("\u001B[32mVerified\u001B[0m 1 release archives"));
        assertFalse(color.stderr().contains("\u001B[36mVerifying release archives...")
                || color.stderr().contains("\u001B[32mVerified 1 release archives"));
        assertEquals(0, quiet.exitCode(), quiet.stderr());
        assertEquals("", quiet.stdout());
    }

    @Test
    void releaseVerifyDefaultsWorkDirectoryFromConfiguredOutputRoot() throws IOException {
        Path projectDir = tempDir.resolve("demo-output-root");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(
                projectDir.resolve("zolt.toml"),
                Files.readString(projectDir.resolve("zolt.toml"))
                        .replace("[build]\n", "[build]\n                outputRoot = \".zolt/build\"\n"));
        CommandResult result = execute(
                "release-verify",
                "--directory", projectDir.toString(),
                "dist/missing.tar.gz");
        assertEquals(1, result.exitCode());
        assertTrue(Files.isDirectory(projectDir.resolve(".zolt/build/release-verify")));
        assertTrue(result.stderr().contains("archive does not exist"));
    }

    private static void writeProjectConfig(Path projectDir, String repositoryUrl) throws IOException {
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

    private static void writeFakeZoltBinary(Path binary) throws IOException {
        Files.createDirectories(binary.getParent());
        Files.writeString(binary, """
                #!/usr/bin/env bash
                set -euo pipefail
                command="${1:-}"
                shift || true

                option_value() {
                  local option="$1"
                  shift
                  while [[ "$#" -gt 0 ]]; do
                    if [[ "$1" == "$option" ]]; then
                      printf '%%s' "$2"
                      return 0
                    fi
                    shift
                  done
                }

                case "$command" in
                  --version)
                    printf '0.1.0\\n'
                    ;;
                  init)
                    cwd="$(option_value --cwd "$@")"
                    name="${@: -1}"
                    mkdir -p "$cwd/$name"
                    cat > "$cwd/$name/zolt.toml" <<'EOF'
                [project]
                name = "smoke"
                version = "0.1.0"
                group = "com.example"
                java = "%s"

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                EOF
                    ;;
                  build)
                    ;;
                  *)
                    printf 'unexpected command: %%s\\n' "$command" >&2
                    exit 2
                    ;;
                esac
                """.formatted(currentJavaMajorVersion()));
        assertTrue(binary.toFile().setExecutable(true));
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
