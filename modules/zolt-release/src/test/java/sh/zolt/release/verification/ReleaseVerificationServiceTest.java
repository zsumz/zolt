package sh.zolt.release.verification;

import static sh.zolt.release.verification.ReleaseVerificationServiceTestSupport.config;
import static sh.zolt.release.verification.ReleaseVerificationServiceTestSupport.writeBinary;
import static sh.zolt.release.verification.ReleaseVerificationServiceTestSupport.writeProjectFiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.release.ReleaseTarget;
import sh.zolt.release.archive.ReleaseArchiveResult;
import sh.zolt.release.archive.ReleaseArchiveService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReleaseVerificationServiceTest {
    @TempDir
    private Path projectDir;

    @Test
    void verifiesArchiveChecksumVersionAndInitSmoke() throws IOException {
        writeProjectFiles(projectDir);
        Path binary = writeBinary(projectDir, "target/native/zolt");
        ReleaseArchiveResult archive = new ReleaseArchiveService().assemble(
                projectDir,
                config(),
                ReleaseTarget.MACOS_ARM64,
                binary,
                Path.of("dist"));
        List<List<String>> commands = new ArrayList<>();
        ReleaseVerificationService service = new ReleaseVerificationService((command, directory) -> {
            commands.add(command);
            if (command.contains("init")) {
                Path cwd = Path.of(command.get(command.indexOf("--cwd") + 1));
                try {
                    Files.createDirectories(cwd.resolve("smoke"));
                    Files.writeString(cwd.resolve("smoke/zolt.toml"), "[project]\n");
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
                return new ReleaseVerificationService.ProcessResult(0, "Created Zolt project at smoke\n");
            }
            return new ReleaseVerificationService.ProcessResult(0, "0.1.0\n");
        });

        ReleaseVerificationResult result = service.verify(
                List.of(archive.archivePath()),
                projectDir.resolve("verify"),
                "0.1.0");

        assertEquals(1, result.verifiedCount());
        ReleaseVerificationResult.VerifiedArchive verified = result.archives().getFirst();
        assertEquals(archive.archivePath(), verified.archivePath());
        assertTrue(Files.exists(verified.unpackDirectory().resolve("zolt-0.1.0-macos-arm64/bin/zolt")));
        assertEquals(3, commands.size());
        assertEquals(List.of(verified.binaryPath().toString(), "--version"), commands.getFirst());
        assertEquals(List.of(
                verified.binaryPath().toString(),
                "init",
                "--cwd",
                verified.unpackDirectory().resolve("smoke-work").toString(),
                "smoke"), commands.get(1));
        assertEquals(List.of(
                verified.binaryPath().toString(),
                "build",
                "--cwd",
                verified.unpackDirectory().resolve("smoke-work/smoke").toString()), commands.get(2));
    }

    @Test
    void verifiesWindowsZipArchive() throws IOException {
        writeProjectFiles(projectDir);
        Path binary = writeBinary(projectDir, "target/native/zolt.exe");
        ReleaseArchiveResult archive = new ReleaseArchiveService().assemble(
                projectDir,
                config(),
                ReleaseTarget.WINDOWS_X64,
                binary,
                Path.of("dist"));
        List<List<String>> commands = new ArrayList<>();
        ReleaseVerificationService service = new ReleaseVerificationService((command, directory) -> {
            commands.add(command);
            if (command.contains("init")) {
                Path cwd = Path.of(command.get(command.indexOf("--cwd") + 1));
                try {
                    Files.createDirectories(cwd.resolve("smoke"));
                    Files.writeString(cwd.resolve("smoke/zolt.toml"), "[project]\n");
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            }
            return new ReleaseVerificationService.ProcessResult(0, "0.1.0\n");
        });

        ReleaseVerificationResult result = service.verify(
                List.of(archive.archivePath()),
                projectDir.resolve("verify-windows"),
                "0.1.0");

        assertEquals(1, result.verifiedCount());
        ReleaseVerificationResult.VerifiedArchive verified = result.archives().getFirst();
        assertTrue(Files.exists(verified.unpackDirectory().resolve("zolt-0.1.0-windows-x64/bin/zolt.exe")));
        assertTrue(Files.exists(verified.unpackDirectory().resolve("zolt-0.1.0-windows-x64/VERSION")));
        assertEquals(List.of(verified.binaryPath().toString(), "--version"), commands.getFirst());
        assertTrue(commands.stream().anyMatch(command -> command.contains("build")));
    }

    @Test
    void defaultRunnerVerifiesExecutableArchiveEndToEnd() throws IOException {
        writeProjectFiles(projectDir);
        Path binary = projectDir.resolve("target/native/zolt");
        Files.createDirectories(binary.getParent());
        Files.writeString(binary, """
                #!/usr/bin/env bash
                set -euo pipefail
                if [[ "${1:-}" == "--version" ]]; then
                  printf '0.1.0\\n'
                  exit 0
                fi
                if [[ "${1:-}" == "init" ]]; then
                  cwd=""
                  while [[ "$#" -gt 0 ]]; do
                    case "$1" in
                      --cwd)
                        cwd="$2"
                        shift 2
                        ;;
                      *)
                        shift
                        ;;
                    esac
                  done
                  mkdir -p "$cwd/smoke"
                  printf '[project]\\n' > "$cwd/smoke/zolt.toml"
                  exit 0
                fi
                if [[ "${1:-}" == "build" ]]; then
                  exit 0
                fi
                exit 1
                """);
        ReleaseArchiveResult archive = new ReleaseArchiveService().assemble(
                projectDir,
                config(),
                ReleaseTarget.LINUX_X64,
                Path.of("target/native/zolt"),
                Path.of("dist"));

        ReleaseVerificationResult result = new ReleaseVerificationService().verify(
                List.of(archive.archivePath()),
                projectDir.resolve("verify-default-runner"),
                "0.1.0");

        assertEquals(1, result.verifiedCount());
        assertTrue(Files.isRegularFile(result.archives().getFirst().binaryPath()));
    }

}
