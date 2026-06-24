package com.zolt.arch;

import static com.zolt.arch.ArchitectureBudgetSupport.describe;
import static com.zolt.arch.ArchitectureBudgetSupport.sourceRoots;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ArchitectureBudgetSupportTest {
    @Test
    void sourceRootsReturnsExistingLiteralRoot(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceRoot);

        assertEquals(List.of(sourceRoot), sourceRoots(sourceRoot));
    }

    @Test
    void sourceRootsReturnsEmptyListForMissingLiteralRoot(@TempDir Path tempDir) throws IOException {
        assertEquals(List.of(), sourceRoots(tempDir.resolve("missing/src/main/java")));
    }

    @Test
    void sourceRootsExpandsWildcardRootsInDeterministicOrder(@TempDir Path tempDir) throws IOException {
        Path beta = tempDir.resolve("modules/beta/src/main/java");
        Path alpha = tempDir.resolve("modules/alpha/src/main/java");
        Files.createDirectories(beta);
        Files.createDirectories(alpha);
        Files.createDirectories(tempDir.resolve("modules/gamma/src/test/java"));

        assertEquals(
                List.of(alpha, beta),
                sourceRoots(tempDir.resolve("modules/*/src/main/java")));
    }

    @Test
    void describeFormatsBulletList() {
        assertEquals("- first\n- second\n", describe(List.of("first", "second")));
    }
}
