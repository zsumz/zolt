package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IncrementalCompileInputHasherTest {
    @TempDir
    private Path tempDir;

    @Test
    void hashesFileContent() throws IOException {
        Path source = tempDir.resolve("src/main/java/com/example/App.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class App {}\n");

        String first = IncrementalCompileInputHasher.hash(source);
        Files.writeString(source, "class App { void changed() {} }\n");
        String second = IncrementalCompileInputHasher.hash(source);

        assertNotEquals(first, second);
    }

    @Test
    void directoryHashIgnoresLocalCompileMetadata() throws IOException {
        Path output = tempDir.resolve("target/classes");
        Files.createDirectories(output.resolve("com/example"));
        Files.writeString(output.resolve("com/example/App.class"), "app");

        String beforeMetadata = IncrementalCompileInputHasher.hash(output);
        Files.writeString(output.resolve(IncrementalCompileState.MAIN_FILE_NAME), "state");
        Files.writeString(output.resolve(".zolt-build-main.fingerprint"), "fingerprint");
        String afterMetadata = IncrementalCompileInputHasher.hash(output);
        Files.writeString(output.resolve("com/example/Other.class"), "other");
        String afterClass = IncrementalCompileInputHasher.hash(output);

        assertEquals(beforeMetadata, afterMetadata);
        assertNotEquals(afterMetadata, afterClass);
    }

    @Test
    void missingInputHashesAsMissing() {
        assertEquals("missing", IncrementalCompileInputHasher.hash(tempDir.resolve("missing.jar")));
    }

    @Test
    void relativePathUsesProjectRelativePathWhenInsideProject() {
        Path projectRoot = tempDir.toAbsolutePath().normalize();

        assertEquals(
                "src/main/java/App.java",
                IncrementalCompileInputHasher.relative(projectRoot, projectRoot.resolve("src/main/java/App.java")));
    }
}
