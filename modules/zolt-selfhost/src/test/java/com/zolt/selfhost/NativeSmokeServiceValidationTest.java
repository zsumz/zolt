package com.zolt.selfhost;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NativeSmokeServiceValidationTest extends NativeSmokeServiceTestSupport {
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
}
