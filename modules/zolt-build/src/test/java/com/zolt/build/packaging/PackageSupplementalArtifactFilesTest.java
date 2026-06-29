package com.zolt.build.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageSupplementalArtifactFilesTest {
    @TempDir
    private Path tempDir;

    @Test
    void sourceFilesIncludesOnlyJavaFilesInEntryOrder() throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java");
        write(sourceRoot.resolve("com/example/Beta.java"));
        write(sourceRoot.resolve("com/example/Alpha.java"));
        write(sourceRoot.resolve("com/example/application.properties"));

        List<Path> files = PackageSupplementalArtifactFiles.sourceFiles(sourceRoot);

        assertEquals(List.of(
                sourceRoot.resolve("com/example/Alpha.java"),
                sourceRoot.resolve("com/example/Beta.java")), files);
    }

    @Test
    void compiledFilesExcludeLocalBuildMetadata() throws IOException {
        Path output = tempDir.resolve("target/test-classes");
        write(output.resolve("com/example/AppTest.class"));
        write(output.resolve(".zolt-build-test.fingerprint"));
        write(output.resolve(".zolt-build-test.fingerprint.state"));
        write(output.resolve(".zolt-incremental-test.state"));

        assertEquals(
                List.of(output.resolve("com/example/AppTest.class")),
                PackageSupplementalArtifactFiles.compiledFiles(output));
    }

    @Test
    void deleteDirectoryRemovesNestedDirectory() throws IOException {
        Path directory = tempDir.resolve("target/javadoc");
        write(directory.resolve("com/example/App.html"));

        PackageSupplementalArtifactFiles.deleteDirectory(directory);

        assertFalse(Files.exists(directory));
    }

    private static void write(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "content");
    }
}
