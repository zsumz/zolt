package com.zolt.selfhost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NativeSmokeServiceTest extends NativeSmokeServiceTestSupport {
    @Test
    void smokesNativeBinaryThroughReleaseAndGeneratedProjectWorkflow() throws IOException {
        Path binary = writeBinary();
        List<List<String>> commands = new ArrayList<>();
        NativeSmokeService service = recordingService(commands);

        NativeSmokeResult result = service.smoke(
                tempDir,
                config(),
                binary,
                Path.of("target/native-smoke"));

        assertEquals(binary, result.binary());
        assertEquals(tempDir.resolve("target/native-smoke"), result.workDirectory());
        assertEquals(tempDir.resolve("target/native-smoke/release/zolt-0.1.0-linux-x64.tar.gz"), result.archive());
        assertEquals(tempDir.resolve("target/native-smoke/hello-native"), result.projectDirectory());
        assertEquals(List.of(binary.toString(), "--version"), commands.getFirst());
        assertEquals(List.of(binary.toString(), "help"), commands.get(1));
        assertEquals(List.of(
                binary.toString(),
                "release-archive",
                "--cwd",
                tempDir.toString(),
                "--binary",
                Path.of("target/native/zolt").toString(),
                "--output",
                Path.of("target/native-smoke/release").toString()), commands.get(2));
        assertEquals(List.of(
                binary.toString(),
                "version",
                "set",
                "--cwd",
                tempDir.resolve("target/native-smoke/hello-native").toString(),
                "--no-resolve",
                "native-smoke",
                "0.0.1"), commands.get(5));
        assertEquals(List.of(
                binary.toString(),
                "version",
                "remove",
                "--cwd",
                tempDir.resolve("target/native-smoke/hello-native").toString(),
                "--no-resolve",
                "native-smoke"), commands.get(6));
        assertEquals(List.of(
                binary.toString(),
                "resolve",
                "--cwd",
                tempDir.resolve("target/native-smoke/hello-native").toString(),
                "--cache-root",
                tempDir.resolve("target/native-smoke/cache").toString()), commands.get(7));
    }

    @Test
    void absoluteWorkDirectoryKeepsReleaseArchiveOutputProjectRelative() throws IOException {
        Path binary = writeBinary();
        List<List<String>> commands = new ArrayList<>();
        NativeSmokeService service = recordingService(commands);

        service.smoke(
                tempDir,
                config(),
                binary,
                tempDir.resolve("target/native-smoke").toAbsolutePath().normalize());

        List<String> releaseArchive = commands.get(2);
        assertEquals(
                Path.of("target/native/zolt").toString(),
                releaseArchive.get(releaseArchive.indexOf("--binary") + 1));
        assertEquals(
                Path.of("target/native-smoke/release").toString(),
                releaseArchive.get(releaseArchive.indexOf("--output") + 1));
    }

    private static NativeSmokeService recordingService(List<List<String>> commands) {
        return new NativeSmokeService((command, directory) -> {
            commands.add(command);
            if (command.contains("release-archive")) {
                Path projectDirectory = Path.of(command.get(command.indexOf("--cwd") + 1));
                Path output = Path.of(command.get(command.indexOf("--output") + 1));
                Path outputDirectory = output.isAbsolute()
                        ? output
                        : projectDirectory.resolve(output).normalize();
                try {
                    Files.createDirectories(outputDirectory);
                    Files.writeString(outputDirectory.resolve("zolt-0.1.0-linux-x64.tar.gz"), "archive");
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
                return new NativeSmokeService.ProcessResult(0, "archive ok\n");
            }
            if (command.contains("init")) {
                Path cwd = Path.of(command.get(command.indexOf("--cwd") + 1));
                try {
                    Files.createDirectories(cwd.resolve("hello-native"));
                    Files.writeString(cwd.resolve("hello-native/zolt.toml"), "[project]\n");
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
                return new NativeSmokeService.ProcessResult(0, "Created Zolt project at hello-native\n");
            }
            if (command.contains("version") && command.contains("set")) {
                Path cwd = Path.of(command.get(command.indexOf("--cwd") + 1));
                try {
                    Files.writeString(cwd.resolve("zolt.toml"), "[project]\n\n[versions]\nnative-smoke = \"0.0.1\"\n");
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
                return new NativeSmokeService.ProcessResult(0, "Added version alias native-smoke = 0.0.1 to [versions]\n");
            }
            if (command.contains("version") && command.contains("remove")) {
                Path cwd = Path.of(command.get(command.indexOf("--cwd") + 1));
                try {
                    Files.writeString(cwd.resolve("zolt.toml"), "[project]\n");
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
                return new NativeSmokeService.ProcessResult(0, "Removed version alias native-smoke from [versions]\n");
            }
            if (command.contains("--version")) {
                return new NativeSmokeService.ProcessResult(0, "0.1.0\n");
            }
            if (command.contains("run")) {
                return new NativeSmokeService.ProcessResult(0, "Hello from hello-native!\n");
            }
            return new NativeSmokeService.ProcessResult(0, "ok\n");
        });
    }
}
