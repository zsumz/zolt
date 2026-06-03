package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeImageRunnerTest {
    @TempDir
    private Path tempDir;

    @Test
    void passesJarRuntimeDependenciesMainClassAndOutputToNativeImage() throws IOException {
        List<List<String>> commands = new ArrayList<>();
        NativeImageRunner runner = new NativeImageRunner(":", command -> {
            commands.add(command);
            return new NativeImageRunner.ProcessResult(0, "native ok\n");
        });
        Path outputBinary = tempDir.resolve("target/native/demo");
        Path logFile = tempDir.resolve("target/native/native-image.log");

        NativeImageResult result = runner.build(new NativeImageRequest(
                Path.of("native-image"),
                Path.of("target/demo.jar"),
                List.of(Path.of("cache/lib.jar")),
                "com.example.Main",
                outputBinary,
                logFile,
                List.of("--no-fallback", "--native-image-info")));

        assertEquals(outputBinary, result.outputBinary());
        assertEquals(logFile, result.logFile());
        assertEquals("native ok\n", result.output());
        assertEquals("native ok\n", Files.readString(logFile));
        assertEquals(List.of(
                "native-image",
                "--no-fallback",
                "--native-image-info",
                "-cp",
                "target/demo.jar:cache/lib.jar",
                "com.example.Main",
                "-o",
                outputBinary.toString()), commands.getFirst());
    }

    @Test
    void nullExecutableUsesNativeImageFromPath() {
        List<List<String>> commands = new ArrayList<>();
        NativeImageRunner runner = new NativeImageRunner(":", command -> {
            commands.add(command);
            return new NativeImageRunner.ProcessResult(0, "");
        });

        runner.build(new NativeImageRequest(
                null,
                Path.of("target/demo.jar"),
                List.of(),
                "com.example.Main",
                tempDir.resolve("demo"),
                tempDir.resolve("native-image.log"),
                List.of()));

        assertEquals("native-image", commands.getFirst().getFirst());
    }

    @Test
    void nonZeroExitWritesLogAndReturnsActionableError() throws IOException {
        NativeImageRunner runner = new NativeImageRunner(":", command ->
                new NativeImageRunner.ProcessResult(3, "missing reflection config\n"));
        Path logFile = tempDir.resolve("target/native/native-image.log");

        NativeImageException exception = assertThrows(
                NativeImageException.class,
                () -> runner.build(new NativeImageRequest(
                        Path.of("native-image"),
                        Path.of("target/demo.jar"),
                        List.of(),
                        "com.example.Main",
                        tempDir.resolve("target/native/demo"),
                        logFile,
                        List.of("--no-fallback"))));

        assertTrue(exception.getMessage().contains("native-image failed with exit code 3"));
        assertTrue(exception.getMessage().contains("Review " + logFile));
        assertTrue(exception.getMessage().contains("missing reflection config"));
        assertEquals("missing reflection config\n", Files.readString(logFile));
    }

    @Test
    void missingMainClassFailsBeforeProcessExecution() {
        NativeImageRunner runner = new NativeImageRunner(":", command -> {
            throw new AssertionError("native-image should not run");
        });

        NativeImageException exception = assertThrows(
                NativeImageException.class,
                () -> runner.build(new NativeImageRequest(
                        Path.of("native-image"),
                        Path.of("target/demo.jar"),
                        List.of(),
                        "",
                        tempDir.resolve("demo"),
                        tempDir.resolve("native-image.log"),
                        List.of())));

        assertTrue(exception.getMessage().contains("Add [project].main"));
    }
}
