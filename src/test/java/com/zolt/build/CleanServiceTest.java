package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CleanServiceTest {
    private final CleanService cleanService = new CleanService();

    @TempDir
    private Path projectDir;

    @Test
    void deletesDefaultTargetDirectory() throws IOException {
        file("target/classes/com/example/Main.class");
        file("target/test-classes/com/example/MainTest.class");
        file("target/generated/sources/annotations/com/example/Generated.java");

        CleanResult result = cleanService.clean(projectDir, BuildSettings.defaults());

        assertEquals(1, result.deletedCount());
        assertEquals(projectDir.resolve("target"), result.deletedPaths().getFirst());
        assertFalse(Files.exists(projectDir.resolve("target")));
    }

    @Test
    void deletesCustomOutputDirectories() throws IOException {
        file("out/main/Main.class");
        file("out-test/test/MainTest.class");
        file("target/generated/sources/annotations/com/example/Generated.java");
        file("target/generated/test-sources/annotations/com/example/GeneratedTest.java");

        CleanResult result = cleanService.clean(
                projectDir,
                new BuildSettings("src/main/java", "src/test/java", "out/main", "out-test/test"),
                CompilerSettings.defaults());

        assertEquals(4, result.deletedCount());
        assertFalse(Files.exists(projectDir.resolve("out/main")));
        assertFalse(Files.exists(projectDir.resolve("out-test/test")));
        assertFalse(Files.exists(projectDir.resolve("target/generated/sources/annotations")));
        assertFalse(Files.exists(projectDir.resolve("target/generated/test-sources/annotations")));
    }

    @Test
    void deletesConfiguredGeneratedSourceDirectories() throws IOException {
        file("out/main/Main.class");
        file("out-test/test/MainTest.class");
        file("build/generated/main/com/example/Generated.java");
        file("build/generated/test/com/example/GeneratedTest.java");

        CleanResult result = cleanService.clean(
                projectDir,
                new BuildSettings("src/main/java", "src/test/java", "out/main", "out-test/test"),
                new CompilerSettings("build/generated/main", "build/generated/test"));

        assertEquals(4, result.deletedCount());
        assertFalse(Files.exists(projectDir.resolve("build/generated/main")));
        assertFalse(Files.exists(projectDir.resolve("build/generated/test")));
    }

    @Test
    void missingOutputDirectoriesAreCleanNoOp() {
        CleanResult result = cleanService.clean(projectDir, BuildSettings.defaults());

        assertEquals(0, result.deletedCount());
    }

    @Test
    void doesNotDeleteGlobalDependencyCache() throws IOException {
        file(".zolt/cache/com/example/app.jar");
        file("target/classes/com/example/Main.class");

        cleanService.clean(projectDir, BuildSettings.defaults());

        assertTrue(Files.exists(projectDir.resolve(".zolt/cache/com/example/app.jar")));
    }

    @Test
    void refusesOutputPathOutsideProject() {
        CleanException exception = assertThrows(
                CleanException.class,
                () -> cleanService.clean(
                        projectDir,
                        new BuildSettings("src/main/java", "src/test/java", "../outside", "target/test-classes")));

        assertTrue(exception.getMessage().contains("Refusing to clean output path"));
    }

    private void file(String path) throws IOException {
        Path file = projectDir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }
}
