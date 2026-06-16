package com.zolt.selfhost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeSmokeServiceTest {
    @TempDir
    private Path tempDir;

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

    @Test
    void workDirectoryCannotBeProjectRoot() throws IOException {
        Path binary = writeBinary();
        NativeSmokeService service = new NativeSmokeService((command, directory) -> {
            throw new AssertionError("native smoke should reject work directory before running commands");
        });

        NativeSmokeException exception = assertThrows(
                NativeSmokeException.class,
                () -> service.smoke(tempDir, config(), binary, Path.of(".")));

        assertTrue(exception.getMessage().contains("--work-dir"));
        assertTrue(exception.getMessage().contains("must not be the project directory itself"));
        assertTrue(Files.exists(binary));
    }

    @Test
    void workDirectoryCannotEscapeProjectRoot() throws IOException {
        Path binary = writeBinary();
        NativeSmokeService service = new NativeSmokeService((command, directory) -> {
            throw new AssertionError("native smoke should reject work directory before running commands");
        });

        NativeSmokeException exception = assertThrows(
                NativeSmokeException.class,
                () -> service.smoke(tempDir, config(), binary, Path.of("../native-smoke-outside")));

        assertTrue(exception.getMessage().contains("--work-dir"));
        assertTrue(exception.getMessage().contains("must be under project directory"));
        assertTrue(Files.exists(binary));
    }

    @Test
    void versionMismatchIsActionable() throws IOException {
        Path binary = writeBinary();
        NativeSmokeService service = new NativeSmokeService((command, directory) ->
                new NativeSmokeService.ProcessResult(0, "demo 0.1.0\n"));

        NativeSmokeException exception = assertThrows(
                NativeSmokeException.class,
                () -> service.smoke(tempDir, config(), binary, Path.of("target/native-smoke")));

        assertTrue(exception.getMessage().contains("expected `"));
        assertTrue(exception.getMessage().contains("0.1.0"));
    }

    @Test
    void missingBinaryIsActionable() {
        NativeSmokeService service = new NativeSmokeService((command, directory) -> {
            throw new AssertionError("binary should not run");
        });

        NativeSmokeException exception = assertThrows(
                NativeSmokeException.class,
                () -> service.smoke(tempDir, config(), Path.of("target/native/missing"), Path.of("target/native-smoke")));

        assertTrue(exception.getMessage().contains("Native smoke requires binary"));
        assertTrue(exception.getMessage().contains("Run `zolt native`"));
    }

    private Path writeBinary() throws IOException {
        Path binary = tempDir.resolve("target/native/zolt");
        Files.createDirectories(binary.getParent());
        Files.writeString(binary, "native");
        return binary;
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

    private static ProjectConfig config() {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("zolt", "0.1.0", "com.zolt", currentJavaMajorVersion(), Optional.of("com.zolt.Main")),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
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
