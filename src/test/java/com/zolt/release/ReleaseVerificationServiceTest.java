package com.zolt.release;

import static com.zolt.release.ReleaseVerificationServiceTestSupport.config;
import static com.zolt.release.ReleaseVerificationServiceTestSupport.writeBinary;
import static com.zolt.release.ReleaseVerificationServiceTestSupport.writeProjectFiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(2, commands.size());
        assertEquals(List.of(verified.binaryPath().toString(), "--version"), commands.getFirst());
        assertEquals(List.of(
                verified.binaryPath().toString(),
                "init",
                "--cwd",
                verified.unpackDirectory().resolve("smoke-work").toString(),
                "smoke"), commands.get(1));
    }

}
